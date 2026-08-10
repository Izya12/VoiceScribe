package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "speakers",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionJobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"])]
)
data class SpeakerEntity(
    @PrimaryKey
    val id: String, // e.g. "jobId_speaker_1"
    val jobId: String,
    val speakerCode: String, // e.g. "speaker_1"
    val displayName: String, // e.g. "Спикер 1" or "Алексей"
    val colorIndex: Int,
    val confidence: Float
)
