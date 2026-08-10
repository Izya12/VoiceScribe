package com.example.data.model

data class TranscriptionConfig(
    val modelId: String = "whisper-base-ggml",
    val isAutoLanguage: Boolean = true,
    val selectedLanguage: String = "ru", // Default Russian
    val enableDiarization: Boolean = true,
    val expectedSpeakerCount: Int = 0, // 0 = automatic
    val enableVAD: Boolean = true,
    val threadCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
    val backendPreference: String = "CPU / NEON" // "CPU / NEON", "GPU / Vulkan", "NNAPI"
)
