package com.example.engine.whisper

import android.content.Context
import com.example.data.local.entity.ModelDescriptorEntity
import com.example.data.model.TranscriptionConfig
import com.example.data.model.VoiceModelConfig
import com.example.engine.SpeechEngine
import com.example.engine.diarization.DiarizedSegment
import java.io.File

/**
 * Адаптер для приведения WhisperEngine к единому интерфейсу SpeechEngine.
 */
class WhisperEngineAdapter(
    private val context: Context,
    private val modelConfig: VoiceModelConfig,
    private val modelFilesDir: File
) : SpeechEngine {

    private val whisperEngine = WhisperEngine()

    override suspend fun transcribe(pcmSamples: FloatArray): TranscriptionResult {
        val modelEntity = ModelDescriptorEntity(
            id = modelConfig.id,
            name = modelConfig.name,
            type = "Whisper",
            quantization = modelConfig.quantization,
            sizeBytes = modelConfig.sizeBytes,
            estimatedRamBytes = modelConfig.estimatedRamBytes,
            supportedLanguages = modelConfig.supportedLanguages,
            downloadUrls = modelConfig.downloadUrls,
            checksumSha256 = modelConfig.checksumsSha256.firstOrNull() ?: "",
            localFilePath = File(modelFilesDir, "${modelConfig.id}.bin").absolutePath,
            isDownloaded = true,
            downloadProgress = 1.0f,
            downloadStatus = "INSTALLED",
            isDefault = modelConfig.isDefault
        )

        val durationUs = (pcmSamples.size.toDouble() / 16000.0 * 1_000_000.0).toLong()
        val defaultSegment = DiarizedSegment(
            startUs = 0L,
            endUs = durationUs,
            speakerCode = "speaker_1",
            confidence = 0.95f
        )

        return whisperEngine.transcribeAudio(
            context = context,
            mediaUri = android.net.Uri.EMPTY,
            pcmSamples = pcmSamples,
            diarizedSegments = listOf(defaultSegment),
            model = modelEntity,
            config = TranscriptionConfig(),
            onProgress = { _, _ -> }
        )
    }

    override fun release() {
        // Очистка локальных ресурсов WhisperEngine при необходимости
    }
}
