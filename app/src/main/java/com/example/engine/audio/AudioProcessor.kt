package com.example.engine.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.roundToInt

class AudioProcessor(private val context: Context) {

    companion object {
        private const val TAG = "AudioProcessor"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TIMEOUT_US = 10000L
    }

    suspend fun extractMetadata(uri: Uri): MediaMetadata = withContext(Dispatchers.IO) {
        var fileName = "media_file"
        var fileSize = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: "media_file"
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query content resolver for metadata", e)
        }

        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var mimeType = "audio/wav"
        var isVideo = false

        try {
            retriever.setDataSource(context, uri)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durStr != null) {
                durationMs = durStr.toLongOrNull() ?: 0L
            }
            val mime = retriever.extractMetadata(12) // METADATA_KEY_MIME_TYPE
            if (mime != null) {
                mimeType = mime
                isVideo = mime.startsWith("video")
            } else {
                isVideo = fileName.endsWith(".mp4") || fileName.endsWith(".mkv") ||
                        fileName.endsWith(".webm") || fileName.endsWith(".mov")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract metadata via MediaMetadataRetriever", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        if (durationMs <= 0L) {
            durationMs = 60_000L
        }

        MediaMetadata(
            uriString = uri.toString(),
            fileName = fileName,
            isVideo = isVideo,
            mimeType = mimeType,
            durationMs = durationMs,
            sizeBytes = fileSize,
            sampleRate = TARGET_SAMPLE_RATE,
            channels = 1
        )
    }

    /**
     * Decodes any media format (MP3, MP4, AAC, M4A, WAV, etc.) from [uri] into
     * 16 kHz Mono 16-bit PCM FloatArray [-1.0f, 1.0f].
     *
     * @param uri Media content URI
     * @param targetDurationMs Optional maximum duration in ms (-1 for full audio)
     */
    suspend fun loadPcmSamples(uri: Uri, targetDurationMs: Long = -1L): FloatArray = withContext(Dispatchers.IO) {
        val rawPcmShorts = decodeToPcmShorts(uri)
        if (rawPcmShorts.data.isEmpty()) {
            Log.e(TAG, "No PCM samples decoded from $uri")
            return@withContext FloatArray(0)
        }

        val sourceSampleRate = rawPcmShorts.sampleRate
        val channelCount = rawPcmShorts.channelCount

        // 1. Convert multi-channel PCM to mono PCM
        val monoShorts = if (channelCount > 1) {
            convertToMono(rawPcmShorts.data, channelCount)
        } else {
            rawPcmShorts.data
        }

        // 2. Resample to 16000 Hz if needed
        val resampledShorts = if (sourceSampleRate != TARGET_SAMPLE_RATE && sourceSampleRate > 0) {
            resampleLinear(monoShorts, sourceSampleRate, TARGET_SAMPLE_RATE)
        } else {
            monoShorts
        }

        // 3. Convert ShortArray to FloatArray [-1.0f, 1.0f]
        var totalSamples = resampledShorts.size
        if (targetDurationMs > 0) {
            val maxSamples = ((targetDurationMs / 1000.0) * TARGET_SAMPLE_RATE).toInt()
            if (totalSamples > maxSamples) {
                totalSamples = maxSamples
            }
        }

        val pcmFloats = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            pcmFloats[i] = resampledShorts[i] / 32768.0f
        }

        Log.i(TAG, "Successfully loaded ${pcmFloats.size} PCM float samples (16kHz mono) for $uri")
        pcmFloats
    }

    private data class DecodedPcmData(
        val data: ShortArray,
        val sampleRate: Int,
        val channelCount: Int
    )

    private fun decodeToPcmShorts(uri: Uri): DecodedPcmData {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var format: MediaFormat? = null
            var mime: String? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val trackMime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (trackMime.startsWith("audio/")) {
                    trackIndex = i
                    format = trackFormat
                    mime = trackMime
                    break
                }
            }

            if (trackIndex < 0 || format == null || mime == null) {
                Log.e(TAG, "No audio track found in $uri")
                return DecodedPcmData(ShortArray(0), TARGET_SAMPLE_RATE, 1)
            }

            extractor.selectTrack(trackIndex)

            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                TARGET_SAMPLE_RATE
            }

            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                1
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmList = ArrayList<ShortArray>()
            var totalShorts = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEof = false
            var isCodecEof = false

            while (!isCodecEof) {
                if (!isExtractorEof) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isExtractorEof = true
                            } else {
                                val presentationTimeUs = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputBufferIndex, 0, sampleSize,
                                    presentationTimeUs, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val chunkShorts = ShortArray(shortBuffer.remaining())
                        shortBuffer.get(chunkShorts)

                        pcmList.add(chunkShorts)
                        totalShorts += chunkShorts.size
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isCodecEof = true
                    }
                }
            }

            val mergedShorts = ShortArray(totalShorts)
            var offset = 0
            for (chunk in pcmList) {
                System.arraycopy(chunk, 0, mergedShorts, offset, chunk.size)
                offset += chunk.size
            }

            return DecodedPcmData(mergedShorts, sampleRate, channelCount)

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio from $uri", e)
            return DecodedPcmData(ShortArray(0), TARGET_SAMPLE_RATE, 1)
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {}
            try {
                codec?.release()
            } catch (_: Exception) {}
            try {
                extractor?.release()
            } catch (_: Exception) {}
        }
    }

    private fun convertToMono(stereo: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return stereo
        val monoLength = stereo.size / channels
        val mono = ShortArray(monoLength)
        for (i in 0 until monoLength) {
            var sum = 0
            for (c in 0 until channels) {
                sum += stereo[i * channels + c].toInt()
            }
            mono[i] = (sum / channels).toShort()
        }
        return mono
    }

    private fun resampleLinear(input: ShortArray, fromRate: Int, toRate: Int): ShortArray {
        if (fromRate == toRate) return input
        val ratio = fromRate.toDouble() / toRate.toDouble()
        val outputLength = (input.size / ratio).roundToInt()
        val output = ShortArray(outputLength)

        for (i in 0 until outputLength) {
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt()
            val fraction = srcPos - srcIndex

            if (srcIndex + 1 < input.size) {
                val sample1 = input[srcIndex].toDouble()
                val sample2 = input[srcIndex + 1].toDouble()
                val interpolated = sample1 + fraction * (sample2 - sample1)
                output[i] = interpolated.roundToInt().coerceIn(-32768, 32767).toShort()
            } else if (srcIndex < input.size) {
                output[i] = input[srcIndex]
            }
        }
        return output
    }
}

