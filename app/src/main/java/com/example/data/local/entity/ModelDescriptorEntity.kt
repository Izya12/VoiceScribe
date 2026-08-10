package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.data.local.converter.StringListConverter

@Entity(tableName = "models")
@TypeConverters(StringListConverter::class)
data class ModelDescriptorEntity(
    @PrimaryKey
    val id: String, // e.g. "whisper-tiny-ggml", "giga-am-v3-e2e-rnnt"
    val name: String,
    val type: String, // "Whisper", "GigaAM (Sherpa-ONNX)"
    val quantization: String, // "Q4_0", "INT8"
    val sizeBytes: Long,
    val estimatedRamBytes: Long,
    val supportedLanguages: String, // "ru", "en, multi"
    val downloadUrls: List<String>,
    val checksumSha256: String,
    val localFilePath: String?,
    val isDownloaded: Boolean,
    val downloadProgress: Float,
    val downloadStatus: String, // "NOT_DOWNLOADED", "DOWNLOADING", "PAUSED", "INSTALLED", "CORRUPTED"
    val isDefault: Boolean
) {
    val downloadUrl: String
        get() = downloadUrls.firstOrNull() ?: ""
}
