package com.example.engine.export

import com.example.data.local.entity.SpeakerEntity
import com.example.data.local.entity.TranscriptSegmentEntity
import com.example.data.local.entity.TranscriptionJobEntity
import com.example.data.model.ExportFormat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class TranscriptExporter {

    fun exportTranscript(
        job: TranscriptionJobEntity,
        segments: List<TranscriptSegmentEntity>,
        speakers: List<SpeakerEntity>,
        format: ExportFormat
    ): String {
        val speakerMap = speakers.associateBy({ it.speakerCode }, { it.displayName })

        return when (format) {
            ExportFormat.TXT -> generateTxt(job, segments, speakerMap)
            ExportFormat.SRT -> generateSrt(segments, speakerMap)
            ExportFormat.VTT -> generateVtt(segments, speakerMap)
            ExportFormat.JSON -> generateJson(job, segments, speakers)
        }
    }

    private fun generateTxt(
        job: TranscriptionJobEntity,
        segments: List<TranscriptSegmentEntity>,
        speakerMap: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("==========================================")
        sb.appendLine("ТРАНСКРИПТ: ${job.mediaName}")
        sb.appendLine("Модель: ${job.modelName}")
        sb.appendLine("Язык: ${job.detectedLanguage}")
        sb.appendLine("Длительность: ${formatDurationText(job.durationMs)}")
        sb.appendLine("==========================================")
        sb.appendLine()

        segments.forEach { seg ->
            val speakerName = speakerMap[seg.speakerId] ?: seg.speakerId
            val timeStr = formatTimestampSrt(seg.startTimeUs)
            sb.appendLine("[$timeStr] $speakerName:")
            sb.appendLine(seg.text)
            sb.appendLine()
        }

        return sb.toString()
    }

    private fun generateSrt(
        segments: List<TranscriptSegmentEntity>,
        speakerMap: Map<String, String>
    ): String {
        val sb = StringBuilder()
        segments.forEachIndexed { index, seg ->
            val speakerName = speakerMap[seg.speakerId] ?: seg.speakerId
            val startStr = formatTimestampSrt(seg.startTimeUs)
            val endStr = formatTimestampSrt(seg.endTimeUs)

            sb.appendLine("${index + 1}")
            sb.appendLine("$startStr --> $endStr")
            sb.appendLine("[$speakerName] ${seg.text}")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun generateVtt(
        segments: List<TranscriptSegmentEntity>,
        speakerMap: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("WEBVTT")
        sb.appendLine()

        segments.forEach { seg ->
            val speakerName = speakerMap[seg.speakerId] ?: seg.speakerId
            val startStr = formatTimestampVtt(seg.startTimeUs)
            val endStr = formatTimestampVtt(seg.endTimeUs)

            sb.appendLine("$startStr --> $endStr")
            sb.appendLine("<v $speakerName>${seg.text}")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun generateJson(
        job: TranscriptionJobEntity,
        segments: List<TranscriptSegmentEntity>,
        speakers: List<SpeakerEntity>
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)

        val metadata = JSONObject()
        metadata.put("jobId", job.id)
        metadata.put("mediaName", job.mediaName)
        metadata.put("mediaType", job.mediaType)
        metadata.put("durationMs", job.durationMs)
        metadata.put("modelName", job.modelName)
        metadata.put("detectedLanguage", job.detectedLanguage)
        metadata.put("languageConfidence", job.languageConfidence)
        metadata.put("processingDurationMs", job.processingDurationMs)
        metadata.put("realTimeFactor", job.realTimeFactor)
        metadata.put("createdAt", job.createdAt)
        root.put("metadata", metadata)

        val speakersArray = JSONArray()
        speakers.forEach { sp ->
            val spObj = JSONObject()
            spObj.put("id", sp.id)
            spObj.put("speakerCode", sp.speakerCode)
            spObj.put("displayName", sp.displayName)
            spObj.put("colorIndex", sp.colorIndex)
            speakersArray.put(spObj)
        }
        root.put("speakers", speakersArray)

        val segmentsArray = JSONArray()
        segments.forEach { seg ->
            val segObj = JSONObject()
            segObj.put("index", seg.segmentIndex)
            segObj.put("startTimeUs", seg.startTimeUs)
            segObj.put("endTimeUs", seg.endTimeUs)
            segObj.put("startTimeFormatted", formatTimestampSrt(seg.startTimeUs))
            segObj.put("endTimeFormatted", formatTimestampSrt(seg.endTimeUs))
            segObj.put("speakerId", seg.speakerId)
            segObj.put("text", seg.text)
            segObj.put("confidence", seg.confidence)
            segmentsArray.put(segObj)
        }
        root.put("segments", segmentsArray)

        return root.toString(2)
    }

    private fun formatTimestampSrt(timeUs: Long): String {
        val totalMs = timeUs / 1000
        val ms = totalMs % 1000
        val totalSeconds = totalMs / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
    }

    private fun formatTimestampVtt(timeUs: Long): String {
        val totalMs = timeUs / 1000
        val ms = totalMs % 1000
        val totalSeconds = totalMs / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    }

    private fun formatDurationText(durMs: Long): String {
        val totalSec = durMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(Locale.US, "%d мин %02d сек", min, sec)
    }
}
