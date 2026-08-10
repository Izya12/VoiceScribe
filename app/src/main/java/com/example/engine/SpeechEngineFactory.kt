package com.example.engine

import android.content.Context
import com.example.data.local.entity.ModelDescriptorEntity
import com.example.data.model.EngineType
import com.example.data.model.VoiceModelConfig
import com.example.engine.whisper.WhisperEngineAdapter
import com.example.util.AppLogger
import java.io.File

/**
 * Фабрика для динамического создания экземпляров SpeechEngine
 * в зависимости от типа модели (WHISPER или SHERPA_ONNX для GigaAM v3).
 */
object SpeechEngineFactory {

    private const val TAG = "SpeechEngineFactory"

    /**
     * Создание соответствующего движка на основе VoiceModelConfig и пути к директории с файлами модели.
     */
    fun createEngine(
        context: Context,
        modelConfig: VoiceModelConfig,
        modelDir: File
    ): SpeechEngine {
        AppLogger.i(TAG, "Создание SpeechEngine [Type: ${modelConfig.engineType}, Model: ${modelConfig.name}]", context)

        return when (modelConfig.engineType) {
            EngineType.WHISPER -> {
                AppLogger.d(TAG, "Инстанцирование WhisperEngineAdapter...", context)
                WhisperEngineAdapter(
                    context = context,
                    modelConfig = modelConfig,
                    modelFilesDir = modelDir
                )
            }
            EngineType.SHERPA_ONNX -> {
                AppLogger.d(TAG, "Инстанцирование GigaAmEngine (Sherpa-ONNX)...", context)
                GigaAmEngine(
                    context = context,
                    modelConfig = modelConfig,
                    modelFilesDir = modelDir
                )
            }
        }
    }

    /**
     * Адаптивный хелпер для создания SpeechEngine из классического ModelDescriptorEntity.
     */
    fun createEngineFromEntity(
        context: Context,
        descriptor: ModelDescriptorEntity
    ): SpeechEngine {
        val isSherpa = descriptor.type.contains("Sherpa", ignoreCase = true) ||
                descriptor.type.contains("Giga", ignoreCase = true) ||
                descriptor.id.contains("giga", ignoreCase = true)

        val engineType = if (isSherpa) EngineType.SHERPA_ONNX else EngineType.WHISPER

        val config = VoiceModelConfig(
            id = descriptor.id,
            name = descriptor.name,
            engineType = engineType,
            quantization = descriptor.quantization,
            sizeBytes = descriptor.sizeBytes,
            estimatedRamBytes = descriptor.estimatedRamBytes,
            supportedLanguages = descriptor.supportedLanguages,
            downloadUrls = listOf(descriptor.downloadUrl),
            localDirectoryPath = descriptor.localFilePath,
            isDownloaded = descriptor.isDownloaded,
            downloadProgress = descriptor.downloadProgress,
            isDefault = descriptor.isDefault
        )

        val modelDir = if (!descriptor.localFilePath.isNullOrBlank()) {
            val file = File(descriptor.localFilePath)
            if (file.isDirectory) file else file.parentFile ?: File(context.filesDir, "models/${descriptor.id}")
        } else {
            File(context.filesDir, "models/${descriptor.id}")
        }

        return createEngine(context, config, modelDir)
    }
}
