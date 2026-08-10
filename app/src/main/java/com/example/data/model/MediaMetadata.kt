package com.example.data.model

data class MediaMetadata(
    val uriString: String,
    val fileName: String,
    val isVideo: Boolean,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRate: Int = 16000,
    val channels: Int = 1
)
