package com.example.engine.diarization

import com.example.engine.vad.SpeechRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class DiarizedSegment(
    val startUs: Long,
    val endUs: Long,
    val speakerCode: String, // "speaker_1", "speaker_2"
    val confidence: Float
)

class SpeakerDiarizer {

    suspend fun processDiarization(
        pcmSamples: FloatArray,
        speechRegions: List<SpeechRegion>,
        expectedSpeakerCount: Int = 0, // 0 = auto
        sampleRate: Int = 16000
    ): List<DiarizedSegment> = withContext(Dispatchers.Default) {
        if (speechRegions.isEmpty()) return@withContext emptyList()

        val diarizedSegments = mutableListOf<DiarizedSegment>()

        // Extract pitch/spectral centroid features for each region
        val regionFeatures = speechRegions.map { region ->
            val startIdx = ((region.startUs / 1_000_000.0) * sampleRate).toInt().coerceIn(0, pcmSamples.size - 1)
            val endIdx = ((region.endUs / 1_000_000.0) * sampleRate).toInt().coerceIn(startIdx + 1, pcmSamples.size)
            
            // Feature 1: Average Abs Energy
            var energy = 0.0f
            var zeroCrossings = 0
            for (i in startIdx until endIdx - 1) {
                energy += abs(pcmSamples[i])
                if ((pcmSamples[i] >= 0 && pcmSamples[i + 1] < 0) || (pcmSamples[i] < 0 && pcmSamples[i + 1] >= 0)) {
                    zeroCrossings++
                }
            }
            val length = (endIdx - startIdx).coerceAtLeast(1)
            val avgEnergy = energy / length
            val zcr = zeroCrossings.toFloat() / length

            // Pseudo-pitch embedding score
            val acousticFeature = zcr * 100.0f + avgEnergy * 10.0f
            acousticFeature
        }

        // Determine speaker count (1 to 4)
        val k = if (expectedSpeakerCount in 1..8) {
            expectedSpeakerCount
        } else {
            // Auto cluster logic based on feature variance
            val minF = regionFeatures.minOrNull() ?: 0.0f
            val maxF = regionFeatures.maxOrNull() ?: 1.0f
            if (maxF - minF > 2.5f && speechRegions.size >= 4) 2 else 1
        }

        if (k <= 1) {
            // Single speaker
            speechRegions.forEach { region ->
                diarizedSegments.add(
                    DiarizedSegment(
                        startUs = region.startUs,
                        endUs = region.endUs,
                        speakerCode = "speaker_1",
                        confidence = 0.95f
                    )
                )
            }
        } else {
            // Multi speaker assignment (Alternate or cluster threshold)
            val minF = regionFeatures.minOrNull() ?: 0.0f
            val maxF = regionFeatures.maxOrNull() ?: 1.0f
            val midF = (minF + maxF) / 2.0f

            speechRegions.forEachIndexed { index, region ->
                val feat = regionFeatures[index]
                val speakerNum = if (feat >= midF) 1 else 2
                diarizedSegments.add(
                    DiarizedSegment(
                        startUs = region.startUs,
                        endUs = region.endUs,
                        speakerCode = "speaker_$speakerNum",
                        confidence = 0.88f
                    )
                )
            }
        }

        diarizedSegments
    }
}
