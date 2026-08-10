#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_engine_whisper_WhisperLib_initContext(
        JNIEnv* env,
        jobject /* this */,
        jstring model_path_jstr) {
    if (model_path_jstr == nullptr) {
        LOGE("Model path is null");
        return 0;
    }

    const char* model_path = env->GetStringUTFChars(model_path_jstr, nullptr);
    if (model_path == nullptr) {
        LOGE("Failed to convert model_path string");
        return 0;
    }

    LOGI("Initializing Whisper context from C++ with file: %s", model_path);
    struct whisper_context* ctx = whisper_init_from_file_with_params(model_path, nullptr);
    env->ReleaseStringUTFChars(model_path_jstr, model_path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper_context from file");
        return 0;
    }

    LOGI("whisper_context initialized successfully at ptr: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_example_engine_whisper_WhisperLib_fullTranscribe(
        JNIEnv* env,
        jobject /* this */,
        jlong context_ptr,
        jint num_threads,
        jfloatArray pcm_samples_jarray,
        jstring language_jstr) {

    if (context_ptr == 0) {
        LOGE("Invalid whisper_context pointer (0L)");
        return env->NewStringUTF("");
    }

    if (pcm_samples_jarray == nullptr) {
        LOGE("PCM samples array is null");
        return env->NewStringUTF("");
    }

    struct whisper_context* ctx = reinterpret_cast<struct whisper_context*>(context_ptr);
    const char* lang = language_jstr ? env->GetStringUTFChars(language_jstr, nullptr) : "ru";
    jsize sample_count = env->GetArrayLength(pcm_samples_jarray);
    jfloat* pcm_data = env->GetFloatArrayElements(pcm_samples_jarray, nullptr);

    if (pcm_data == nullptr) {
        LOGE("Failed to get PCM float array elements");
        if (language_jstr && lang) env->ReleaseStringUTFChars(language_jstr, lang);
        return env->NewStringUTF("");
    }

    LOGI("Executing whisper_full_parallel in C++: %d samples, %d threads, lang=%s", sample_count, num_threads, lang ? lang : "ru");

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.language = lang;
    params.print_progress = false;
    params.print_special = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    int res = whisper_full_parallel(ctx, params, pcm_data, sample_count, 1);

    env->ReleaseFloatArrayElements(pcm_samples_jarray, pcm_data, JNI_ABORT);
    if (language_jstr && lang) {
        env->ReleaseStringUTFChars(language_jstr, lang);
    }

    if (res != 0) {
        LOGE("whisper_full_parallel error code: %d", res);
        return env->NewStringUTF("");
    }

    std::string full_text = "";
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char* seg_text = whisper_full_get_segment_text(ctx, i);
        if (seg_text != nullptr) {
            full_text += seg_text;
        }
    }

    LOGI("Whisper C++ transcription complete. Result length: %zu", full_text.length());
    return env->NewStringUTF(full_text.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_engine_whisper_WhisperLib_freeContext(
        JNIEnv* env,
        jobject /* this */,
        jlong context_ptr) {
    if (context_ptr != 0) {
        struct whisper_context* ctx = reinterpret_cast<struct whisper_context*>(context_ptr);
        LOGI("Freeing whisper_context C++ pointer: %p", ctx);
        whisper_free(ctx);
    }
}

} // extern "C"

