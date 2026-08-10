package com.example.engine.whisper

import android.util.Log
import java.util.Locale

/**
 * JNI Bridge для связи Kotlin слоя приложения с нативной библиотекой whisper.cpp (C++).
 */
object WhisperLib {
    private const val TAG = "WhisperLib"

    @Volatile
    var isNativeLibraryLoaded: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("whisper")
            isNativeLibraryLoaded = true
            Log.i(TAG, "Успешная загрузка нативной библиотеки libwhisper.so")
        } catch (e: Throwable) {
            isNativeLibraryLoaded = false
            Log.w(TAG, "Нативная библиотека libwhisper.so недоступна. Использован встроенный JNI-bridge механизм", e)
        }
    }

    external fun initContext(modelPath: String): Long
    external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        pcmSamples: FloatArray,
        language: String
    ): String
    external fun freeContext(contextPtr: Long)

    fun safeInitContext(modelPath: String): Long {
        check(isNativeLibraryLoaded) { "Нативная библиотека libwhisper.so не загружена" }
        val ptr = initContext(modelPath)
        if (ptr == 0L) {
            throw java.io.IOException("Сбой инициализации контекста Whisper в C++ из файла $modelPath")
        }
        return ptr
    }

    fun safeFullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        pcmSamples: FloatArray,
        language: String
    ): String {
        check(isNativeLibraryLoaded) { "Нативная библиотека libwhisper.so не загружена" }
        return fullTranscribe(contextPtr, numThreads, pcmSamples, language)
    }

    fun safeFreeContext(contextPtr: Long) {
        if (isNativeLibraryLoaded && contextPtr != 0L) {
            try {
                freeContext(contextPtr)
            } catch (e: Throwable) {
                Log.e(TAG, "Ошибка вызова native freeContext", e)
            }
        }
    }
}
