package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcription_jobs")
data class TranscriptionJobEntity(
    @PrimaryKey
    val id: String,
    val mediaUri: String,
    val mediaName: String,
    val mediaType: String, // "audio" or "video"
    val mimeType: String,
    val durationMs: Long,
    val fileSizeMs: Long,
    val modelId: String,
    val modelName: String,
    val languageMode: String, // "AUTO" or "MANUAL"
    val selectedLanguage: String, // e.g. "ru", "en", "auto"
    val detectedLanguage: String,
    val languageConfidence: Float,
    val status: String, // CREATED, RUNNING_TRANSCRIPTION, COMPLETED, FAILED, CANCELLED
    val progress: Float, // 0.0 to 1.0
    val currentStage: String, // e.g. "VAD", "Diarization", "Whisper Inference"
    val enableDiarization: Boolean,
    val expectedSpeakerCount: Int,
    val enableVAD: Boolean,
    val threadCount: Int,
    val createdAt: Long,
    val startedAt: Long,
    val completedAt: Long,
    val processingDurationMs: Long,
    val realTimeFactor: Float, // processingDuration / duration
    val errorMessage: String? = null
)
