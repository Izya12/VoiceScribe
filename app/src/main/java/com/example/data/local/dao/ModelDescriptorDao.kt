package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ModelDescriptorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDescriptorDao {
    @Query("SELECT * FROM models")
    fun getAllModels(): Flow<List<ModelDescriptorEntity>>

    @Query("SELECT * FROM models")
    suspend fun getAllModelsList(): List<ModelDescriptorEntity>

    @Query("SELECT * FROM models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): ModelDescriptorEntity?

    @Query("SELECT * FROM models WHERE isDownloaded = 1 LIMIT 1")
    suspend fun getAnyDownloadedModel(): ModelDescriptorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<ModelDescriptorEntity>)

    @Update
    suspend fun updateModel(model: ModelDescriptorEntity)

    @Query("UPDATE models SET isDownloaded = :isDownloaded, downloadProgress = :progress, downloadStatus = :status, localFilePath = :filePath WHERE id = :modelId")
    suspend fun updateDownloadState(modelId: String, isDownloaded: Boolean, progress: Float, status: String, filePath: String?)

    @Query("UPDATE models SET isDefault = (id = :modelId)")
    suspend fun setDefaultModel(modelId: String)
}
