package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SpeakerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerDao {
    @Query("SELECT * FROM speakers WHERE jobId = :jobId")
    fun getSpeakersForJob(jobId: String): Flow<List<SpeakerEntity>>

    @Query("SELECT * FROM speakers WHERE jobId = :jobId")
    suspend fun getSpeakersForJobSync(jobId: String): List<SpeakerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakers(speakers: List<SpeakerEntity>)

    @Update
    suspend fun updateSpeaker(speaker: SpeakerEntity)

    @Query("UPDATE speakers SET displayName = :newName WHERE id = :speakerId")
    suspend fun renameSpeaker(speakerId: String, newName: String)
}
