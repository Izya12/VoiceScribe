package com.example

import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.TranscriptSegmentEntity
import com.example.data.local.entity.TranscriptionJobEntity
import com.example.data.model.ExportFormat
import com.example.data.model.JobState
import com.example.engine.export.TranscriptExporter
import com.example.engine.vad.VoiceActivityDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAndVADTest {

    @Test
    fun testSrtExportFormat() {
        val exporter = TranscriptExporter()
        val job = TranscriptionJobEntity(
            id = "job_test_1",
            mediaUri = "content://test",
            mediaName = "test_audio.mp3",
            mediaType = "audio",
            mimeType = "audio/mp3",
            durationMs = 60000L,
            fileSizeMs = 1000000L,
            modelId = "whisper-base-ggml",
            modelName = "Whisper Base",
            languageMode = "AUTO",
            selectedLanguage = "ru",
            detectedLanguage = "ru",
            languageConfidence = 0.98f,
            status = JobState.COMPLETED.name,
            progress = 1.0f,
            currentStage = "COMPLETED",
            enableDiarization = true,
            expectedSpeakerCount = 2,
            enableVAD = true,
            threadCount = 4,
            createdAt = System.currentTimeMillis(),
            startedAt = System.currentTimeMillis(),
            completedAt = System.currentTimeMillis() + 5000,
            processingDurationMs = 5000L,
            realTimeFactor = 0.08f
        )

        val segments = listOf(
            TranscriptSegmentEntity(
                id = 1L,
                jobId = "job_test_1",
                segmentIndex = 0,
                startTimeUs = 0L,
                endTimeUs = 3_000_000L,
                speakerId = "speaker_1",
                text = "Здравствуйте, это тестовая транскрипция.",
                confidence = 0.95f,
                language = "ru"
            )
        )

        val speakers = listOf(
            SpeakerEntity(
                id = "job_test_1_speaker_1",
                jobId = "job_test_1",
                speakerCode = "speaker_1",
                displayName = "Алексей",
                colorIndex = 0,
                confidence = 0.95f
            )
        )

        val srtOutput = exporter.exportTranscript(job, segments, speakers, ExportFormat.SRT)
        assertTrue(srtOutput.contains("00:00:00,000 --> 00:00:03,000"))
        assertTrue(srtOutput.contains("[Алексей] Здравствуйте, это тестовая транскрипция."))
    }

    @Test
    fun testVADRegionDetection() = runBlocking {
        val vad = VoiceActivityDetector()
        val pcm = FloatArray(16000 * 2) { 0.5f } // 2 seconds of high amplitude PCM signal
        val regions = vad.detectSpeechRegions(pcm, sampleRate = 16000, frameSizeMs = 30)

        assertTrue(regions.isNotEmpty())
        assertEquals(0L, regions[0].startUs)
    }
}
