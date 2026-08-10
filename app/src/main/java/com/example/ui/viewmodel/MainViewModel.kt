package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ModelDescriptorEntity
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.TranscriptSegmentEntity
import com.example.data.local.entity.TranscriptionJobEntity
import com.example.data.model.ExportFormat
import com.example.data.model.JobState
import com.example.data.model.MediaMetadata
import com.example.data.model.TranscriptionConfig
import com.example.data.repository.ModelRepository
import com.example.data.repository.TranscriptionRepository
import com.example.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HardwareInfo(
    val cpuAbi: String = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val totalMemoryMb: Long = (Runtime.getRuntime().maxMemory() / (1024 * 1024)),
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val hasVulkan: Boolean = true,
    val hasNNAPI: Boolean = true
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val transcriptionRepo = TranscriptionRepository(
        context = application,
        jobDao = db.transcriptionJobDao(),
        segmentDao = db.transcriptSegmentDao(),
        speakerDao = db.speakerDao(),
        modelDao = db.modelDescriptorDao()
    )
    private val modelRepo = ModelRepository(
        context = application,
        modelDao = db.modelDescriptorDao()
    )

    val allJobs: StateFlow<List<TranscriptionJobEntity>> = transcriptionRepo.allJobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allModels: StateFlow<List<ModelDescriptorEntity>> = modelRepo.allModels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeJob: StateFlow<TranscriptionJobEntity?> = allJobs.combine(MutableStateFlow(Unit)) { jobs, _ ->
        jobs.firstOrNull { it.status != JobState.COMPLETED.name && it.status != JobState.FAILED.name && it.status != JobState.CANCELLED.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedMedia = MutableStateFlow<MediaMetadata?>(null)
    val selectedMedia: StateFlow<MediaMetadata?> = _selectedMedia.asStateFlow()

    private val _transcriptionConfig = MutableStateFlow(TranscriptionConfig())
    val transcriptionConfig: StateFlow<TranscriptionConfig> = _transcriptionConfig.asStateFlow()

    private val _hardwareInfo = MutableStateFlow(HardwareInfo())
    val hardwareInfo: StateFlow<HardwareInfo> = _hardwareInfo.asStateFlow()

    private val _exportedContent = MutableStateFlow<String?>(null)
    val exportedContent: StateFlow<String?> = _exportedContent.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    fun saveGeminiApiKey(key: String) {
        _geminiApiKey.value = key.trim()
        getApplication<Application>().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("gemini_api_key", key.trim())
            .apply()
    }

    init {
        viewModelScope.launch {
            modelRepo.seedDefaultModelsIfEmpty()
            modelRepo.syncDatabaseWithFileSystem()
            transcriptionRepo.seedSampleJobsIfEmpty()
        }
    }

    fun selectMediaUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val metadata = transcriptionRepo.analyzeMedia(uri)
                _selectedMedia.value = metadata
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearSelectedMedia() {
        _selectedMedia.value = null
    }

    fun updateConfig(config: TranscriptionConfig) {
        _transcriptionConfig.value = config
    }

    fun startTranscription(uri: Uri) {
        val media = _selectedMedia.value ?: return
        val config = _transcriptionConfig.value
        viewModelScope.launch {
            try {
                transcriptionRepo.startTranscriptionJob(uri, media, config)
                _selectedMedia.value = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            transcriptionRepo.cancelJob(jobId)
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            transcriptionRepo.deleteJob(jobId)
        }
    }

    fun getSegmentsFlow(jobId: String): StateFlow<List<TranscriptSegmentEntity>> {
        return transcriptionRepo.getSegmentsFlow(jobId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getSpeakersFlow(jobId: String): StateFlow<List<SpeakerEntity>> {
        return transcriptionRepo.getSpeakersFlow(jobId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getJobFlow(jobId: String): StateFlow<TranscriptionJobEntity?> {
        return transcriptionRepo.getJobFlow(jobId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    fun renameSpeaker(speakerId: String, newName: String) {
        viewModelScope.launch {
            transcriptionRepo.renameSpeaker(speakerId, newName)
        }
    }

    fun exportTranscript(jobId: String, format: ExportFormat) {
        viewModelScope.launch {
            try {
                val content = transcriptionRepo.exportTranscriptContent(jobId, format)
                _exportedContent.value = content
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearExportedContent() {
        _exportedContent.value = null
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            try {
                modelRepo.downloadModel(modelId) { progress ->
                    // Progress updated via Room DB
                }
            } catch (e: Exception) {
                AppLogger.e("MainViewModel", "Ошибка при скачивании модели $modelId", e, getApplication())
            }
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepo.deleteModel(modelId)
        }
    }

    fun setDefaultModel(modelId: String) {
        viewModelScope.launch {
            modelRepo.setDefaultModel(modelId)
            _transcriptionConfig.value = _transcriptionConfig.value.copy(modelId = modelId)
        }
    }
}
