package com.example.engine.vad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class SpeechRegion(
    val startUs: Long,
    val endUs: Long,
    val energyScore: Float
)

class VoiceActivityDetector {

    suspend fun detectSpeechRegions(
        pcmSamples: FloatArray,
        sampleRate: Int = 16000,
        frameSizeMs: Int = 30,
        energyThreshold: Float = 0.02f
    ): List<SpeechRegion> = withContext(Dispatchers.Default) {
        val frameSamples = (sampleRate * frameSizeMs) / 1000
        val totalFrames = pcmSamples.size / frameSamples
        val isSpeechFrame = BooleanArray(totalFrames)

        for (f in 0 until totalFrames) {
            val offset = f * frameSamples
            var sumSquare = 0.0f
            for (i in 0 until frameSamples) {
                val sample = pcmSamples[offset + i]
                sumSquare += sample * sample
            }
            val rms = sqrt(sumSquare / frameSamples)
            isSpeechFrame[f] = rms >= energyThreshold
        }

        // Merge contiguous speech frames into SpeechRegions
        val regions = mutableListOf<SpeechRegion>()
        var inSpeech = false
        var startFrame = 0
        var speechEnergySum = 0.0f

        for (f in 0 until totalFrames) {
            if (isSpeechFrame[f]) {
                if (!inSpeech) {
                    inSpeech = true
                    startFrame = f
                    speechEnergySum = 0.0f
                }
                speechEnergySum += 1.0f
            } else {
                if (inSpeech) {
                    val durationFrames = f - startFrame
                    // Filter out tiny noise bursts (<150ms = 5 frames)
                    if (durationFrames >= 5) {
                        val startUs = (startFrame.toLong() * frameSizeMs) * 1000L
                        val endUs = (f.toLong() * frameSizeMs) * 1000L
                        regions.add(
                            SpeechRegion(
                                startUs = startUs,
                                endUs = endUs,
                                energyScore = (speechEnergySum / durationFrames).coerceIn(0.5f, 1.0f)
                            )
                        )
                    }
                    inSpeech = false
                }
            }
        }

        if (inSpeech) {
            val startUs = (startFrame.toLong() * frameSizeMs) * 1000L
            val endUs = (totalFrames.toLong() * frameSizeMs) * 1000L
            regions.add(
                SpeechRegion(
                    startUs = startUs,
                    endUs = endUs,
                    energyScore = 0.85f
                )
            )
        }

        // Fallback: If no speech was detected, create full audio region
        if (regions.isEmpty() && pcmSamples.isNotEmpty()) {
            val totalDurationUs = ((pcmSamples.size.toDouble() / sampleRate) * 1_000_000).toLong()
            regions.add(
                SpeechRegion(
                    startUs = 0L,
                    endUs = totalDurationUs,
                    energyScore = 0.75f
                )
            )
        }

        regions
    }
}
