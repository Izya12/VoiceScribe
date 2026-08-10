package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.TranscriptSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptSegmentDao {
    @Query("SELECT * FROM transcript_segments WHERE jobId = :jobId ORDER BY segmentIndex ASC")
    fun getSegmentsForJob(jobId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE jobId = :jobId ORDER BY segmentIndex ASC")
    suspend fun getSegmentsForJobSync(jobId: String): List<TranscriptSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Query("DELETE FROM transcript_segments WHERE jobId = :jobId")
    suspend fun deleteSegmentsForJob(jobId: String)
}
