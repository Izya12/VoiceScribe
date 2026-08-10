package com.example.data.repository

import android.content.Context
import com.example.data.local.dao.ModelDescriptorDao
import com.example.data.local.entity.ModelDescriptorEntity
import com.example.data.model.EngineType
import com.example.data.model.VoiceModelConfig
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ModelRepository(
    private val context: Context,
    private val modelDao: ModelDescriptorDao
) {
    private val downloadManager = ModelDownloadManager(context)

    val allModels: Flow<List<ModelDescriptorEntity>> = modelDao.getAllModels()

    suspend fun seedDefaultModelsIfEmpty() = withContext(Dispatchers.IO) {
        val gigaAmConfig = VoiceModelConfig.GIGA_AM_V3_E2E_RNNT

        val defaultList = listOf(
            ModelDescriptorEntity(
                id = gigaAmConfig.id,
                name = gigaAmConfig.name,
                type = "GigaAM (Sherpa-ONNX)",
                quantization = gigaAmConfig.quantization,
                sizeBytes = gigaAmConfig.sizeBytes,
                estimatedRamBytes = gigaAmConfig.estimatedRamBytes,
                supportedLanguages = gigaAmConfig.supportedLanguages,
                downloadUrls = gigaAmConfig.downloadUrls,
                checksumSha256 = gigaAmConfig.checksumsSha256.firstOrNull() ?: "",
                localFilePath = null,
                isDownloaded = false,
                downloadProgress = 0.0f,
                downloadStatus = "NOT_DOWNLOADED",
                isDefault = true
            ),
            ModelDescriptorEntity(
                id = "whisper-base-ggml",
                name = "Whisper Base (Q4_0)",
                type = "Whisper",
                quantization = "Q4_0",
                sizeBytes = 147 * 1024 * 1024L,
                estimatedRamBytes = 500 * 1024 * 1024L,
                supportedLanguages = "ru, en, multi",
                downloadUrls = listOf("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"),
                checksumSha256 = "60ed5f73940176868846c20d7feb307f",
                localFilePath = null,
                isDownloaded = false,
                downloadProgress = 0.0f,
                downloadStatus = "NOT_DOWNLOADED",
                isDefault = false
            ),
            ModelDescriptorEntity(
                id = "whisper-tiny-ggml",
                name = "Whisper Tiny (Q4_0)",
                type = "Whisper",
                quantization = "Q4_0",
                sizeBytes = 77 * 1024 * 1024L,
                estimatedRamBytes = 250 * 1024 * 1024L,
                supportedLanguages = "ru, en, multi",
                downloadUrls = listOf("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"),
                checksumSha256 = "be0702a207a7267ee641db21f2461b1d",
                localFilePath = null,
                isDownloaded = false,
                downloadProgress = 0.0f,
                downloadStatus = "NOT_DOWNLOADED",
                isDefault = false
            ),
            ModelDescriptorEntity(
                id = "whisper-small-ggml",
                name = "Whisper Small (Q4_0)",
                type = "Whisper",
                quantization = "Q4_0",
                sizeBytes = 466 * 1024 * 1024L,
                estimatedRamBytes = 1000 * 1024 * 1024L,
                supportedLanguages = "ru, en, multi",
                downloadUrls = listOf("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"),
                checksumSha256 = "1be3a9a20d2d3a3915f013d1e44f83d0",
                localFilePath = null,
                isDownloaded = false,
                downloadProgress = 0.0f,
                downloadStatus = "NOT_DOWNLOADED",
                isDefault = false
            )
        )

        val existing = modelDao.getAllModelsList()
        if (existing.isEmpty()) {
            modelDao.insertModels(defaultList)
        } else {
            val updatedList = defaultList.map { def ->
                val cur = existing.find { it.id == def.id }
                if (cur != null) {
                    cur.copy(
                        downloadUrls = def.downloadUrls,
                        sizeBytes = def.sizeBytes
                    )
                } else {
                    def
                }
            }
            modelDao.insertModels(updatedList)
        }
    }

    suspend fun syncDatabaseWithFileSystem() = withContext(Dispatchers.IO) {
        val models = modelDao.getAllModelsList()
        for (model in models) {
            if (model.isDownloaded) {
                val isSherpa = model.type.contains("Sherpa", ignoreCase = true) || model.type.contains("Giga", ignoreCase = true)
                var isValid = true

                if (isSherpa) {
                    val modelDir = if (!model.localFilePath.isNullOrBlank()) File(model.localFilePath) else File(context.filesDir, "models/${model.id}")
                    val requiredFiles = listOf("encoder.onnx", "decoder.onnx", "tokens.txt")
                    if (!modelDir.exists() || !modelDir.isDirectory) {
                        isValid = false
                    } else {
                        for (req in requiredFiles) {
                            val f = File(modelDir, req)
                            if (!f.exists() || f.length() == 0L) {
                                isValid = false
                                break
                            }
                        }
                        val joiner = File(modelDir, "joiner.onnx")
                        val joint = File(modelDir, "joint.onnx")
                        if ((!joiner.exists() || joiner.length() == 0L) && (!joint.exists() || joint.length() == 0L)) {
                            isValid = false
                        }
                    }
                } else {
                    val file = if (!model.localFilePath.isNullOrBlank()) File(model.localFilePath) else File(context.filesDir, "models/${model.id}.bin")
                    if (!file.exists() || file.length() == 0L) {
                        isValid = false
                    }
                }

                if (!isValid) {
                    AppLogger.w("ModelRepository", "Модель ${model.name} (${model.id}) была помечена как скачанная, но файлы отсутствуют или равны 0 байтам. Сброс состояния.", context)
                    modelDao.updateDownloadState(
                        modelId = model.id,
                        isDownloaded = false,
                        progress = 0.0f,
                        status = "NOT_DOWNLOADED",
                        filePath = null
                    )
                }
            }
        }
    }

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val model = modelDao.getModelById(modelId) ?: return@withContext
        modelDao.updateDownloadState(modelId, false, 0.01f, "DOWNLOADING", null)

        val isSherpa = model.type.contains("Sherpa", ignoreCase = true) || model.type.contains("Giga", ignoreCase = true)
        val engineType = if (isSherpa) EngineType.SHERPA_ONNX else EngineType.WHISPER

        val config = VoiceModelConfig(
            id = model.id,
            name = model.name,
            engineType = engineType,
            quantization = model.quantization,
            sizeBytes = model.sizeBytes,
            estimatedRamBytes = model.estimatedRamBytes,
            supportedLanguages = model.supportedLanguages,
            downloadUrls = model.downloadUrls,
            checksumsSha256 = listOf(model.checksumSha256)
        )

        try {
            val downloadedDir = downloadManager.downloadModelGroup(config) { progress ->
                onProgress(progress)
                modelDao.updateDownloadState(modelId, false, progress, "DOWNLOADING", null)
            }

            modelDao.updateDownloadState(
                modelId = modelId,
                isDownloaded = true,
                progress = 1.0f,
                status = "INSTALLED",
                filePath = downloadedDir.absolutePath
            )
        } catch (e: Exception) {
            AppLogger.e("ModelRepository", "Сбой скачивания модели $modelId: ${e.message}", e, context)
            modelDao.updateDownloadState(
                modelId = modelId,
                isDownloaded = false,
                progress = 0.0f,
                status = "ERROR",
                filePath = null
            )
            throw e
        }
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = modelDao.getModelById(modelId) ?: return@withContext
        if (model.localFilePath != null) {
            try {
                File(model.localFilePath).deleteRecursively()
            } catch (_: Exception) {}
        }
        modelDao.updateDownloadState(modelId, false, 0.0f, "NOT_DOWNLOADED", null)
    }

    suspend fun setDefaultModel(modelId: String) = withContext(Dispatchers.IO) {
        modelDao.setDefaultModel(modelId)
    }
}

