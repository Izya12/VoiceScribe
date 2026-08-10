package com.example.data.model

enum class EngineType {
    WHISPER,
    SHERPA_ONNX
}

data class VoiceModelConfig(
    val id: String,
    val name: String,
    val engineType: EngineType,
    val quantization: String = "INT8",
    val sizeBytes: Long,
    val estimatedRamBytes: Long,
    val supportedLanguages: String = "ru",
    val downloadUrls: List<String>,
    val checksumsSha256: List<String> = emptyList(),
    val localDirectoryPath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0.0f,
    val isDefault: Boolean = false
) {
    companion object {
        /**
         * Конфигурация русскоязычной модели GigaAM v3 e2e_rnnt (Sherpa-ONNX)
         * Содержит ссылки на 4 файла: encoder, decoder, joint и tokens.
         */
        val GIGA_AM_V3_E2E_RNNT = VoiceModelConfig(
            id = "giga-am-v3-e2e-rnnt",
            name = "GigaAM v3 e2e_rnnt (Sherpa-ONNX)",
            engineType = EngineType.SHERPA_ONNX,
            quantization = "INT8",
            sizeBytes = 420 * 1024 * 1024L, // ~420MB
            estimatedRamBytes = 800 * 1024 * 1024L, // ~800MB RAM
            supportedLanguages = "ru",
            downloadUrls = listOf(
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-giga-am-russian-2024-10-24/resolve/main/encoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-giga-am-russian-2024-10-24/resolve/main/decoder.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-giga-am-russian-2024-10-24/resolve/main/joiner.int8.onnx",
                "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-giga-am-russian-2024-10-24/resolve/main/tokens.txt"
            ),
            checksumsSha256 = emptyList()
        )

        val WHISPER_BASE = VoiceModelConfig(
            id = "whisper-base-ggml",
            name = "Whisper Base (Q4_0)",
            engineType = EngineType.WHISPER,
            quantization = "Q4_0",
            sizeBytes = 147 * 1024 * 1024L,
            estimatedRamBytes = 500 * 1024 * 1024L,
            supportedLanguages = "ru, en, multi",
            downloadUrls = listOf(
                "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
            )
        )
    }
}
