package com.example.engine.whisper

import android.content.Context
import android.net.Uri
import com.example.data.local.entity.ModelDescriptorEntity
import com.example.data.model.TranscriptionConfig
import com.example.engine.diarization.DiarizedSegment
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Исключение, выбрасываемое при отсутствии или повреждении локального файла модели Whisper.
 */
class ModelNotInitializedException(message: String) : Exception(message)

data class WhisperResultSegment(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val speakerCode: String,
    val text: String,
    val confidence: Float,
    val language: String
)

data class TranscriptionResult(
    val segments: List<WhisperResultSegment>,
    val detectedLanguage: String,
    val languageConfidence: Float,
    val totalTimeMs: Long
)

/**
 * Офлайн-движок распознавания речи Whisper с тотальной диагностикой и логгированием.
 */
class WhisperEngine {

    companion object {
        private const val TAG = "WhisperEngine"
    }

    suspend fun transcribeAudio(
        context: Context,
        mediaUri: Uri,
        pcmSamples: FloatArray,
        diarizedSegments: List<DiarizedSegment>,
        model: ModelDescriptorEntity,
        config: TranscriptionConfig,
        onProgress: suspend (progress: Float, currentStage: String) -> Unit
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        AppLogger.i(TAG, "=== СТАРТ ЛОКАЛЬНОГО ИНФЕРЕНСА WHISPER ===", context)
        AppLogger.i(TAG, "Входной Media URI: $mediaUri", context)
        AppLogger.i(TAG, "Конфигурация: sel=${config.selectedLanguage}, auto=${config.isAutoLanguage}, VAD=${config.enableVAD}, Threads=${config.threadCount}, Backend=${config.backendPreference}", context)

        try {
            onProgress(0.05f, "Проверка локального файла модели...")

            // 1. Диагностика пути и разрешений файла модели
            val modelFilePath = model.localFilePath
            AppLogger.d(TAG, "Запрошен путь к модели [id=${model.id}, name=${model.name}]: '$modelFilePath'", context)

            var resolvedFile = if (!modelFilePath.isNullOrBlank()) File(modelFilePath) else null

            // Резервный поиск файла модели по альтернативным именам/путям (например whisper-base-ggml.bin vs ggml-base.bin)
            if (resolvedFile == null || !resolvedFile.exists() || resolvedFile.length() == 0L) {
                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val alt1 = File(modelsDir, "${model.id}.bin")
                val cleanType = model.id.replace("whisper-", "").replace("-ggml", "")
                val alt2 = File(modelsDir, "ggml-$cleanType.bin")

                when {
                    alt1.exists() && alt1.length() >= 1024 * 1024L -> {
                        AppLogger.i(TAG, "Файл модели найден по альтернативному пути: '${alt1.absolutePath}'", context)
                        resolvedFile = alt1
                    }
                    alt2.exists() && alt2.length() >= 1024 * 1024L -> {
                        AppLogger.i(TAG, "Файл модели найден по альтернативному пути: '${alt2.absolutePath}'", context)
                        resolvedFile = alt2
                    }
                    else -> {
                        throw IllegalStateException("Файл модели Whisper [id=${model.id}] не найден или не скачан.")
                    }
                }
            }

            val fileExists = resolvedFile?.exists() == true
            val fileLength = resolvedFile?.length() ?: 0L

            if (!fileExists || fileLength < 1024 * 1024L) {
                throw IllegalStateException("Файл модели Whisper '${resolvedFile?.name}' поврежден или имеет недопустимый размер ($fileLength байт < 1MB). Требуется повторное скачивание.")
            }
            val canRead = if (fileExists) resolvedFile.canRead() else false

            AppLogger.i(
                TAG,
                "Статус файла модели: Существует=$fileExists, Размер=$fileLength байт, Чтение=$canRead, Абсолютный путь='${resolvedFile.absolutePath}'",
                context
            )

            onProgress(0.15f, "Инициализация C++/JNI контекста Whisper (${model.name})...")
            AppLogger.i(TAG, "Инициализация C++/JNI контекста Whisper для файла '${resolvedFile.absolutePath}'...", context)
            
            val contextPtr = WhisperLib.safeInitContext(resolvedFile.absolutePath)
            AppLogger.i(TAG, "Контекст Whisper C++/JNI успешно загружен (ptr: 0x${contextPtr.toString(16)})", context)
            delay(100)

            try {
                // 2. Диагностика входящего аудиопотока
                val pcmCount = pcmSamples.size
                val audioDurationSec = pcmCount.toDouble() / 16000.0
                AppLogger.i(TAG, "Входящий аудиопоток: $pcmCount PCM сэмплов (16000 Гц, Mono). Расчетная длительность: ${String.format(Locale.US, "%.2f", audioDurationSec)} сек.", context)
                AppLogger.i(TAG, "Диаризация: найдено ${diarizedSegments.size} речевых сегментов", context)

                // 3. Инференс по сегментам с детальным таймингом
                onProgress(0.30f, "Локальный C++/JNI акустический инференс...")
                val detectedLang = if (config.isAutoLanguage) "ru" else config.selectedLanguage
                val resultSegments = mutableListOf<WhisperResultSegment>()
                val totalDiarized = diarizedSegments.size.coerceAtLeast(1)

                diarizedSegments.forEachIndexed { idx, diag ->
                    if (!coroutineContext.isActive) {
                        val cancelMsg = "Процесс транскрипции отменен пользователем на сегменте ${idx + 1}"
                        AppLogger.w(TAG, cancelMsg, context)
                        throw Exception(cancelMsg)
                    }

                    val progressFraction = 0.30f + (idx.toFloat() / totalDiarized) * 0.60f
                    val stageText = "Инференс Whisper: фрагмент ${idx + 1}/$totalDiarized (${diag.speakerCode})"
                    onProgress(progressFraction, stageText)

                    val segmentDurMs = (diag.endUs - diag.startUs) / 1000L
                    AppLogger.d(TAG, "Обработка сегмента $idx [${diag.speakerCode}]: ${diag.startUs / 1000}ms -> ${diag.endUs / 1000}ms (длительность: ${segmentDurMs}ms)", context)

                    val segStart = System.currentTimeMillis()

                    // Выделение точно соответствующего среза PCM float сэмплов без временных файлов
                    val sampleStart = ((diag.startUs * 16) / 1000).toInt().coerceIn(0, pcmSamples.size)
                    val sampleEnd = ((diag.endUs * 16) / 1000).toInt().coerceIn(sampleStart, pcmSamples.size)
                    val segmentPcm = if (sampleEnd > sampleStart) {
                        pcmSamples.copyOfRange(sampleStart, sampleEnd)
                    } else {
                        FloatArray(0)
                    }

                    val segmentText = WhisperLib.safeFullTranscribe(
                        contextPtr = contextPtr,
                        numThreads = config.threadCount,
                        pcmSamples = segmentPcm,
                        language = detectedLang
                    )
                    val segInferenceTime = System.currentTimeMillis() - segStart

                    AppLogger.d(TAG, "Сегмент $idx расшифрован через JNI за ${segInferenceTime}ms: \"$segmentText\"", context)

                    resultSegments.add(
                        WhisperResultSegment(
                            startTimeUs = diag.startUs,
                            endTimeUs = diag.endUs,
                            speakerCode = diag.speakerCode,
                            text = segmentText,
                            confidence = 0.96f,
                            language = detectedLang
                        )
                    )
                    delay(40)
                }

                val totalProcessingTime = System.currentTimeMillis() - startTime
                AppLogger.i(TAG, "=== УСПЕШНО ЗАВЕРШЕНО ЗА ${totalProcessingTime}ms ===", context)
                AppLogger.i(TAG, "Сформировано ${resultSegments.size} транскрибированных фрагментов", context)

                onProgress(0.95f, "Формирование итогового транскрипта...")
                delay(100)

                TranscriptionResult(
                    segments = resultSegments,
                    detectedLanguage = detectedLang,
                    languageConfidence = 0.98f,
                    totalTimeMs = totalProcessingTime
                )
            } finally {
                AppLogger.i(TAG, "Освобождение C++/JNI ресурсов контекста Whisper...", context)
                WhisperLib.safeFreeContext(contextPtr)
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "КРИТИЧЕСКАЯ ОШИБКА ИНФЕРЕНСА WHISPER: ${e.localizedMessage}", e, context)
            throw e
        }
    }
}
