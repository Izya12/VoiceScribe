package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TranscriptionJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionJobDao {
    @Query("SELECT * FROM transcription_jobs ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<TranscriptionJobEntity>>

    @Query("SELECT * FROM transcription_jobs WHERE id = :jobId")
    fun getJobByIdFlow(jobId: String): Flow<TranscriptionJobEntity?>

    @Query("SELECT * FROM transcription_jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: String): TranscriptionJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: TranscriptionJobEntity)

    @Update
    suspend fun updateJob(job: TranscriptionJobEntity)

    @Query("SELECT COUNT(*) FROM transcription_jobs")
    suspend fun getJobCount(): Int

    @Query("DELETE FROM transcription_jobs WHERE id = :jobId")
    suspend fun deleteJobById(jobId: String)

    @Query("UPDATE transcription_jobs SET status = :status, progress = :progress, currentStage = :stage WHERE id = :jobId")
    suspend fun updateJobProgress(jobId: String, status: String, progress: Float, stage: String)

    @Query("UPDATE transcription_jobs SET status = 'CANCELLED' WHERE id = :jobId")
    suspend fun cancelJob(jobId: String)
}
