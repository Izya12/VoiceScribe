package com.example.engine

import android.content.Context
import com.example.data.model.VoiceModelConfig
import com.example.engine.whisper.TranscriptionResult
import com.example.engine.whisper.WhisperResultSegment
import com.example.util.AppLogger
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Единый интерфейс для оффлайн-движков распознавания речи (Whisper, GigaAM/Sherpa-ONNX).
 */
interface SpeechEngine {
    suspend fun transcribe(pcmSamples: FloatArray): TranscriptionResult
    fun release()
}

/**
 * Имплементация SpeechEngine для русскоязычной модели GigaAM v3 (e2e_rnnt)
 * с использованием библиотеки com.k2fsa.sherpa.onnx (OfflineRecognizer).
 */
class GigaAmEngine(
    private val context: Context,
    private val modelConfig: VoiceModelConfig,
    private val modelFilesDir: File
) : SpeechEngine {

    companion object {
        private const val TAG = "GigaAmEngine"
    }

    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    private val encoderFile: File = File(modelFilesDir, "encoder.int8.onnx").let { if (it.exists()) it else File(modelFilesDir, "encoder.onnx") }
    private val decoderFile: File = File(modelFilesDir, "decoder.int8.onnx").let { if (it.exists()) it else File(modelFilesDir, "decoder.onnx") }
    private val jointFile: File = File(modelFilesDir, "joiner.int8.onnx").let {
        if (it.exists()) it else File(modelFilesDir, "joiner.onnx").let { j ->
            if (j.exists()) j else File(modelFilesDir, "joint.onnx")
        }
    }
    private val tokensFile: File = File(modelFilesDir, "tokens.txt")

    private val encoderPath: String = encoderFile.absolutePath
    private val decoderPath: String = decoderFile.absolutePath
    private val jointPath: String = jointFile.absolutePath
    private val tokensPath: String = tokensFile.absolutePath

    init {
        initSherpaOnnxRecognizer()
    }

    private fun initSherpaOnnxRecognizer() {
        AppLogger.i(TAG, "Инициализация Sherpa-ONNX OfflineRecognizer для GigaAM...", context)
        
        val onnxFiles = listOf(encoderFile, decoderFile, jointFile)
        for (file in onnxFiles) {
            if (!file.exists() || file.length() < 1024 * 1024L) {
                throw IllegalStateException("Файл ONNX модели ${file.name} не существует или поврежден (размер: ${file.length()} байт < 1MB)")
            }
        }

        if (!tokensFile.exists() || tokensFile.length() < 50L) {
            throw IllegalStateException("Файл токенов ${tokensFile.name} не существует или поврежден (размер: ${tokensFile.length()} байт < 50B)")
        }

        AppLogger.d(TAG, "Encoder path: $encoderPath (${File(encoderPath).length()} bytes)", context)
        AppLogger.d(TAG, "Decoder path: $decoderPath (${File(decoderPath).length()} bytes)", context)
        AppLogger.d(TAG, "Joint path: $jointPath (${File(jointPath).length()} bytes)", context)
        AppLogger.d(TAG, "Tokens path: $tokensPath (${File(tokensPath).length()} bytes)", context)

        val transducerConfig = OfflineTransducerModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            joint = jointPath
        )
        val offlineModelConfig = OfflineModelConfig(
            transducer = transducerConfig,
            tokens = tokensPath,
            numThreads = 4,
            debug = false,
            provider = "cpu"
        )
        val recognizerConfig = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = offlineModelConfig
        )

        recognizer = OfflineRecognizer(assetManager = null, config = recognizerConfig)
        isInitialized = true
        AppLogger.i(TAG, "Контекст GigaAM v3 (Sherpa-ONNX) успешно инициализирован", context)
    }

    override suspend fun transcribe(pcmSamples: FloatArray): TranscriptionResult = withContext(Dispatchers.Default) {
        val activeRecognizer = recognizer ?: throw IOException("OfflineRecognizer GigaAM v3 не инициализирован")
        val startTime = System.currentTimeMillis()
        val sampleCount = pcmSamples.size
        val durationUs = (sampleCount.toDouble() / 16000.0 * 1_000_000.0).toLong()

        AppLogger.i(TAG, "Старт инференса GigaAM v3 ($sampleCount PCM сэмплов, 16000 Гц)...", context)

        val stream = activeRecognizer.createStream()
        stream.acceptWaveform(pcmSamples, 16000)
        activeRecognizer.decode(stream)
        val result = activeRecognizer.getResult(stream)
        val recognizedText = result.text
        stream.release()

        val elapsedTime = System.currentTimeMillis() - startTime

        TranscriptionResult(
            segments = listOf(
                WhisperResultSegment(
                    startTimeUs = 0L,
                    endTimeUs = durationUs,
                    speakerCode = "speaker_1",
                    text = recognizedText,
                    confidence = 0.98f,
                    language = "ru"
                )
            ),
            detectedLanguage = "ru",
            languageConfidence = 0.99f,
            totalTimeMs = elapsedTime
        )
    }

    override fun release() {
        if (isInitialized) {
            AppLogger.i(TAG, "Освобождение C++ ресурсов Sherpa-ONNX OfflineRecognizer...", context)
            recognizer?.release()
            recognizer = null
            isInitialized = false
        }
    }
}
