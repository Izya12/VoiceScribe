package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.dao.ModelDescriptorDao
import com.example.data.local.dao.SpeakerDao
import com.example.data.local.dao.TranscriptSegmentDao
import com.example.data.local.dao.TranscriptionJobDao
import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.TranscriptSegmentEntity
import com.example.data.local.entity.TranscriptionJobEntity
import com.example.data.model.ExportFormat
import com.example.data.model.JobState
import com.example.data.model.MediaMetadata
import com.example.data.model.TranscriptionConfig
import com.example.engine.audio.AudioProcessor
import com.example.engine.diarization.SpeakerDiarizer
import com.example.engine.export.TranscriptExporter
import com.example.engine.vad.VoiceActivityDetector
import com.example.engine.whisper.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class TranscriptionRepository(
    private val context: Context,
    private val jobDao: TranscriptionJobDao,
    private val segmentDao: TranscriptSegmentDao,
    private val speakerDao: SpeakerDao,
    private val modelDao: ModelDescriptorDao
) {
    private val audioProcessor = AudioProcessor(context)
    private val vad = VoiceActivityDetector()
    private val diarizer = SpeakerDiarizer()
    private val whisperEngine = WhisperEngine()
    private val exporter = TranscriptExporter()

    val allJobs: Flow<List<TranscriptionJobEntity>> = jobDao.getAllJobs()

    suspend fun seedSampleJobsIfEmpty() = withContext(Dispatchers.IO) {
        if (jobDao.getJobCount() == 0) {
            // Job 1
            val job1Id = "sample_job_chemistry"
            val job1 = TranscriptionJobEntity(
                id = job1Id,
                mediaUri = "content://media/sample_chemistry.mp4",
                mediaName = "Контракция_растворов_Мастер_Зи.mp4",
                mediaType = "video",
                mimeType = "video/mp4",
                durationMs = 77000L,
                fileSizeMs = 12500000L,
                modelId = "whisper-base-ggml",
                modelName = "Whisper Base (Q4_0)",
                languageMode = "AUTO",
                selectedLanguage = "ru",
                detectedLanguage = "ru",
                languageConfidence = 0.99f,
                status = JobState.COMPLETED.name,
                progress = 1.0f,
                currentStage = "Завершено",
                enableDiarization = true,
                expectedSpeakerCount = 1,
                enableVAD = true,
                threadCount = 4,
                createdAt = System.currentTimeMillis() - 3600000,
                startedAt = System.currentTimeMillis() - 3590000,
                completedAt = System.currentTimeMillis() - 3584000,
                processingDurationMs = 6000L,
                realTimeFactor = 0.08f
            )
            jobDao.insertJob(job1)

            val sp1 = SpeakerEntity(
                id = "${job1Id}_speaker_1",
                jobId = job1Id,
                speakerCode = "speaker_1",
                displayName = "Мастер Зи",
                colorIndex = 0,
                confidence = 0.98f
            )
            speakerDao.insertSpeakers(listOf(sp1))

            val segs1 = listOf(
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 0, startTimeUs = 0L, endTimeUs = 3_500_000L, speakerId = "speaker_1", text = "«Бесполезные знания», Мастер Зи.", confidence = 0.98f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 1, startTimeUs = 3_500_000L, endTimeUs = 9_000_000L, speakerId = "speaker_1", text = "Слово дня: «контракция», и почему каждый должен знать, что же это такое?", confidence = 0.97f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 2, startTimeUs = 9_000_000L, endTimeUs = 18_000_000L, speakerId = "speaker_1", text = "Небольшая задача: если мы возьмем 0,5 л воды и 0,5 л спирта и смешаем их вместе, что мы получим?", confidence = 0.99f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 3, startTimeUs = 18_000_000L, endTimeUs = 24_500_000L, speakerId = "speaker_1", text = "И я слышу ваши радостные возгласы: водку — безусловно! Но вот сколько?", confidence = 0.96f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 4, startTimeUs = 24_500_000L, endTimeUs = 38_000_000L, speakerId = "speaker_1", text = "Я напоминаю: пол-литра воды, пол-литра спирта... Но, к сожалению, из-за этой самой «контракции» на литр водки нам рассчитывать не приходится.", confidence = 0.97f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 5, startTimeUs = 38_000_000L, endTimeUs = 51_000_000L, speakerId = "speaker_1", text = "Дело в том, что образуется при смешивании гидрат спирта, молекула которого намного плотнее, чем молекулы воды или спирта.", confidence = 0.98f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 6, startTimeUs = 51_000_000L, endTimeUs = 58_500_000L, speakerId = "speaker_1", text = "Соответственно, на выходе мы получим всего-навсего 964 миллилитра водки.", confidence = 0.99f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 7, startTimeUs = 58_500_000L, endTimeUs = 73_000_000L, speakerId = "speaker_1", text = "Интересно, что этот процесс впервые описал в своей диссертации 17-ый ребёнок Тобольского директора гимназии Дмитрий Иванович Менделеев.", confidence = 0.96f, language = "ru"),
                TranscriptSegmentEntity(jobId = job1Id, segmentIndex = 8, startTimeUs = 73_000_000L, endTimeUs = 77_000_000L, speakerId = "speaker_1", text = "Теперь вы знаете!", confidence = 0.99f, language = "ru")
            )
            segmentDao.insertSegments(segs1)

            // Job 2
            val job2Id = "sample_job_grammar_dialogue"
            val job2 = TranscriptionJobEntity(
                id = job2Id,
                mediaUri = "content://media/sample_grammar.mp4",
                mediaName = "Сочинение_без_помощи_Мамы.mp4",
                mediaType = "video",
                mimeType = "video/mp4",
                durationMs = 21000L,
                fileSizeMs = 4200000L,
                modelId = "whisper-small-ggml",
                modelName = "Whisper Small (Q4_0)",
                languageMode = "MANUAL",
                selectedLanguage = "ru",
                detectedLanguage = "ru",
                languageConfidence = 0.98f,
                status = JobState.COMPLETED.name,
                progress = 1.0f,
                currentStage = "Завершено",
                enableDiarization = true,
                expectedSpeakerCount = 2,
                enableVAD = true,
                threadCount = 4,
                createdAt = System.currentTimeMillis() - 1800000,
                startedAt = System.currentTimeMillis() - 1790000,
                completedAt = System.currentTimeMillis() - 1788000,
                processingDurationMs = 2000L,
                realTimeFactor = 0.09f
            )
            jobDao.insertJob(job2)

            val sp21 = SpeakerEntity(
                id = "${job2Id}_speaker_1",
                jobId = job2Id,
                speakerCode = "speaker_1",
                displayName = "Сын",
                colorIndex = 1,
                confidence = 0.96f
            )
            val sp22 = SpeakerEntity(
                id = "${job2Id}_speaker_2",
                jobId = job2Id,
                speakerCode = "speaker_2",
                displayName = "Мама",
                colorIndex = 2,
                confidence = 0.95f
            )
            speakerDao.insertSpeakers(listOf(sp21, sp22))

            val segs2 = listOf(
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 0, startTimeUs = 0L, endTimeUs = 2_500_000L, speakerId = "speaker_1", text = "Ходил на встречу с королём в пальте.", confidence = 0.97f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 1, startTimeUs = 2_500_000L, endTimeUs = 4_000_000L, speakerId = "speaker_2", text = "Пальто не склоняется.", confidence = 0.98f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 2, startTimeUs = 4_000_000L, endTimeUs = 6_000_000L, speakerId = "speaker_1", text = "Перед королём все склоняются!", confidence = 0.99f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 3, startTimeUs = 6_000_000L, endTimeUs = 9_000_000L, speakerId = "speaker_2", text = "Нет такого правила в русском языке.", confidence = 0.96f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 4, startTimeUs = 9_000_000L, endTimeUs = 13_000_000L, speakerId = "speaker_1", text = "А я не доверяю языку, в котором слово «глагол» — это существительное.", confidence = 0.98f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 5, startTimeUs = 13_000_000L, endTimeUs = 16_000_000L, speakerId = "speaker_1", text = "А слово «существительное» — это прилагательное.", confidence = 0.97f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 6, startTimeUs = 16_000_000L, endTimeUs = 19_000_000L, speakerId = "speaker_1", text = "По сути, только прилагательное — прилагательное.", confidence = 0.95f, language = "ru"),
                TranscriptSegmentEntity(jobId = job2Id, segmentIndex = 7, startTimeUs = 19_000_000L, endTimeUs = 21_000_000L, speakerId = "speaker_2", text = "Ну что скажешь?", confidence = 0.99f, language = "ru")
            )
            segmentDao.insertSegments(segs2)
        }
    }

    fun getJobFlow(jobId: String): Flow<TranscriptionJobEntity?> = jobDao.getJobByIdFlow(jobId)

    fun getSegmentsFlow(jobId: String): Flow<List<TranscriptSegmentEntity>> = segmentDao.getSegmentsForJob(jobId)

    fun getSpeakersFlow(jobId: String): Flow<List<SpeakerEntity>> = speakerDao.getSpeakersForJob(jobId)

    suspend fun analyzeMedia(uri: Uri): MediaMetadata {
        return audioProcessor.extractMetadata(uri)
    }

    suspend fun startTranscriptionJob(
        mediaUri: Uri,
        mediaMetadata: MediaMetadata,
        config: TranscriptionConfig
    ): String = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val requestedModel = modelDao.getModelById(config.modelId)
        val model = if (requestedModel?.isDownloaded == true) {
            requestedModel
        } else {
            modelDao.getAnyDownloadedModel() ?: requestedModel ?: throw Exception("Не найдена подходящая модель")
        }

        val initialJob = TranscriptionJobEntity(
            id = jobId,
            mediaUri = mediaUri.toString(),
            mediaName = mediaMetadata.fileName,
            mediaType = if (mediaMetadata.isVideo) "video" else "audio",
            mimeType = mediaMetadata.mimeType,
            durationMs = mediaMetadata.durationMs,
            fileSizeMs = mediaMetadata.sizeBytes,
            modelId = model.id,
            modelName = model.name,
            languageMode = if (config.isAutoLanguage) "AUTO" else "MANUAL",
            selectedLanguage = config.selectedLanguage,
            detectedLanguage = "Загрузка...",
            languageConfidence = 0.0f,
            status = JobState.CREATED.name,
            progress = 0.05f,
            currentStage = JobState.CREATED.displayNameRu,
            enableDiarization = config.enableDiarization,
            expectedSpeakerCount = config.expectedSpeakerCount,
            enableVAD = config.enableVAD,
            threadCount = config.threadCount,
            createdAt = System.currentTimeMillis(),
            startedAt = System.currentTimeMillis(),
            completedAt = 0L,
            processingDurationMs = 0L,
            realTimeFactor = 0.0f
        )

        jobDao.insertJob(initialJob)

        // Run processing pipeline asynchronously
        runPipeline(jobId, mediaUri, mediaMetadata, config, model)

        jobId
    }

    private suspend fun runPipeline(
        jobId: String,
        uri: Uri,
        metadata: MediaMetadata,
        config: TranscriptionConfig,
        model: com.example.data.local.entity.ModelDescriptorEntity
    ) = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        com.example.util.AppLogger.i("TranscriptionRepository", "Старт пайплайна транскрипции [Job ID: $jobId, media: ${metadata.fileName}]", context)

        if (!model.isDownloaded) {
            val errorMsg = "Модель '${model.name}' не скачана. Скачайте модель в разделе 'Модели' перед транскрипцией."
            com.example.util.AppLogger.e("TranscriptionRepository", errorMsg, null, context)
            val currentJob = jobDao.getJobById(jobId)
            if (currentJob != null) {
                jobDao.updateJob(
                    currentJob.copy(
                        status = JobState.FAILED.name,
                        currentStage = "Ошибка: Модель не скачана",
                        errorMessage = errorMsg
                    )
                )
            }
            return@withContext
        }

        try {
            // 1. Audio Preprocessing
            updateJobState(jobId, JobState.PREPARING_AUDIO, 0.15f)
            com.example.util.AppLogger.d("TranscriptionRepository", "Декодирование PCM сэмплов из URI '$uri' (длительность: ${metadata.durationMs}ms)...", context)
            val pcmSamples = audioProcessor.loadPcmSamples(uri, metadata.durationMs)
            com.example.util.AppLogger.d("TranscriptionRepository", "Успешно декодировано ${pcmSamples.size} PCM сэмплов", context)

            // 2. VAD
            updateJobState(jobId, JobState.RUNNING_VAD, 0.30f)
            val speechRegions = if (config.enableVAD) {
                vad.detectSpeechRegions(pcmSamples)
            } else {
                listOf(com.example.engine.vad.SpeechRegion(0L, metadata.durationMs * 1000L, 1.0f))
            }
            com.example.util.AppLogger.d("TranscriptionRepository", "VAD обнаружено ${speechRegions.size} речевых зон", context)

            // 3. Diarization
            updateJobState(jobId, JobState.RUNNING_DIARIZATION, 0.45f)
            val diarizedSegments = if (config.enableDiarization) {
                diarizer.processDiarization(pcmSamples, speechRegions, config.expectedSpeakerCount)
            } else {
                speechRegions.map {
                    com.example.engine.diarization.DiarizedSegment(
                        startUs = it.startUs,
                        endUs = it.endUs,
                        speakerCode = "speaker_1",
                        confidence = 0.95f
                    )
                }
            }
            com.example.util.AppLogger.d("TranscriptionRepository", "Диаризация завершена: ${diarizedSegments.size} сегментов", context)

            // Save Speaker entities
            val uniqueSpeakers = diarizedSegments.map { it.speakerCode }.distinct()
            val speakerEntities = uniqueSpeakers.mapIndexed { idx, spCode ->
                val num = spCode.removePrefix("speaker_").toIntOrNull() ?: (idx + 1)
                SpeakerEntity(
                    id = "${jobId}_$spCode",
                    jobId = jobId,
                    speakerCode = spCode,
                    displayName = "Спикер $num",
                    colorIndex = idx % 6,
                    confidence = 0.95f
                )
            }
            speakerDao.insertSpeakers(speakerEntities)

            // 4. Speech Engine (Whisper / GigaAM Sherpa-ONNX) Inference
            updateJobState(jobId, JobState.RUNNING_TRANSCRIPTION, 0.60f)
            com.example.util.AppLogger.i("TranscriptionRepository", "Инстанцирование SpeechEngine через SpeechEngineFactory для модели '${model.name}' [Type: ${model.type}]...", context)
            
            val speechEngine = com.example.engine.SpeechEngineFactory.createEngineFromEntity(context, model)
            val transcriptionResult = speechEngine.transcribe(pcmSamples)
            speechEngine.release()

            // 5. Assembling Result & Save Segments to Room
            updateJobState(jobId, JobState.ASSEMBLING_RESULT, 0.95f)
            val segmentEntities = transcriptionResult.segments.mapIndexed { idx, seg ->
                val matchingSpeaker = diarizedSegments.find { ds ->
                    ds.startUs <= seg.startTimeUs && ds.endUs >= seg.endTimeUs
                }?.speakerCode ?: seg.speakerCode

                TranscriptSegmentEntity(
                    jobId = jobId,
                    segmentIndex = idx,
                    startTimeUs = seg.startTimeUs,
                    endTimeUs = seg.endTimeUs,
                    speakerId = matchingSpeaker,
                    text = seg.text,
                    confidence = seg.confidence,
                    language = seg.language
                )
            }
            segmentDao.insertSegments(segmentEntities)

            // Calculate metrics & complete job
            val endTime = System.currentTimeMillis()
            val totalProcessingMs = endTime - startTime
            val rtf = if (metadata.durationMs > 0) totalProcessingMs.toFloat() / metadata.durationMs else 0.5f

            val currentJob = jobDao.getJobById(jobId)
            if (currentJob != null) {
                val updatedJob = currentJob.copy(
                    status = JobState.COMPLETED.name,
                    progress = 1.0f,
                    currentStage = JobState.COMPLETED.displayNameRu,
                    detectedLanguage = transcriptionResult.detectedLanguage,
                    languageConfidence = transcriptionResult.languageConfidence,
                    completedAt = endTime,
                    processingDurationMs = totalProcessingMs,
                    realTimeFactor = rtf
                )
                jobDao.updateJob(updatedJob)
            }
            com.example.util.AppLogger.i("TranscriptionRepository", "Успешное завершение работы над заданием $jobId (время: ${totalProcessingMs}ms, RTF: $rtf)", context)

        } catch (e: Exception) {
            com.example.util.AppLogger.e("TranscriptionRepository", "Сбой в пайплайне обработки задания $jobId: ${e.message}", e, context)
            val currentJob = jobDao.getJobById(jobId)
            if (currentJob != null) {
                jobDao.updateJob(
                    currentJob.copy(
                        status = JobState.FAILED.name,
                        currentStage = "Ошибка: ${e.message}",
                        errorMessage = e.message ?: "Произошла неизвестная ошибка при распознавании"
                    )
                )
            }
        }
    }

    private suspend fun updateJobState(jobId: String, state: JobState, progress: Float) {
        jobDao.updateJobProgress(jobId, state.name, progress, state.displayNameRu)
    }

    suspend fun renameSpeaker(speakerId: String, newName: String) = withContext(Dispatchers.IO) {
        speakerDao.renameSpeaker(speakerId, newName)
    }

    suspend fun deleteJob(jobId: String) = withContext(Dispatchers.IO) {
        jobDao.deleteJobById(jobId)
    }

    suspend fun cancelJob(jobId: String) = withContext(Dispatchers.IO) {
        jobDao.cancelJob(jobId)
    }

    suspend fun exportTranscriptContent(jobId: String, format: ExportFormat): String = withContext(Dispatchers.IO) {
        val job = jobDao.getJobById(jobId) ?: throw Exception("Задание не найдено")
        val segments = segmentDao.getSegmentsForJobSync(jobId)
        val speakers = speakerDao.getSpeakersForJobSync(jobId)
        exporter.exportTranscript(job, segments, speakers, format)
    }
}
