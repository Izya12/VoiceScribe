package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

data class FeatureConfig(
    val sampleRate: Int = 16000,
    val featureDim: Int = 80
)

data class OfflineTransducerModelConfig(
    val encoder: String = "",
    val decoder: String = "",
    val joint: String = ""
)

data class OfflineModelConfig(
    val transducer: OfflineTransducerModelConfig = OfflineTransducerModelConfig(),
    val tokens: String = "",
    val numThreads: Int = 4,
    val debug: Boolean = false,
    val provider: String = "cpu"
)

data class OfflineRecognizerConfig(
    val featConfig: FeatureConfig = FeatureConfig(),
    val modelConfig: OfflineModelConfig = OfflineModelConfig()
)

data class OfflineRecognizerResult(
    val text: String
)

class OfflineStream(val ptr: Long) {
    fun acceptWaveform(samples: FloatArray, sampleRate: Int) {}
    fun release() {}
}

class OfflineRecognizer(
    val assetManager: AssetManager? = null,
    val config: OfflineRecognizerConfig
) {
    init {
        try {
            System.loadLibrary("sherpa-onnx-jni")
        } catch (_: Throwable) {}
    }

    fun createStream(): OfflineStream {
        return OfflineStream(1001L)
    }

    fun decode(stream: OfflineStream) {}

    fun getResult(stream: OfflineStream): OfflineRecognizerResult {
        return OfflineRecognizerResult(text = "")
    }

    fun release() {}
}
