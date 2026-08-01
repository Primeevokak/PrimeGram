// PrimeGram: JNI bridge to whisper.cpp for on-device voice transcription.
//
// Deliberately built as its own libprimewhisper.so rather than folded into libtmessages:
// ggml is a large, fast-moving dependency, and keeping it separate means a failure to build,
// load or run it can never take the messenger down with it. The Java side loads this library
// lazily — only when the user has actually turned offline transcription on and downloaded a
// model — so devices that never use the feature never map it into memory.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "whisper.h"

#define LOG_TAG "PrimeWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_org_telegram_messenger_PrimeWhisper_nativeInit(JNIEnv *env, jclass clazz, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    if (path == nullptr) {
        return 0;
    }
    whisper_context_params cparams = whisper_context_default_params();
    // No GPU backend is compiled in; asking for one would only cost a failed probe.
    cparams.use_gpu = false;
    cparams.flash_attn = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (ctx == nullptr) {
        LOGE("failed to load model");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_org_telegram_messenger_PrimeWhisper_nativeFree(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr != 0) {
        whisper_free(reinterpret_cast<whisper_context *>(ptr));
    }
}

// What the streaming listener needs while whisper is running.
//
// whisper_full is synchronous and calls its segment callback on the thread that called it, so the
// JNIEnv captured here is the right one and no thread has to be attached. Anything else - a
// callback delivered from a worker inside ggml - would need attaching and would make this a good
// deal more delicate than it is.
struct segment_sink {
    JNIEnv *env;
    jobject listener;
    jmethodID method;
    std::string text;
};

static void on_new_segment(struct whisper_context *ctx, struct whisper_state *state, int n_new,
                           void *user_data) {
    auto *sink = static_cast<segment_sink *>(user_data);
    if (sink == nullptr || sink->listener == nullptr || sink->method == nullptr) {
        return;
    }
    const int total = whisper_full_n_segments(ctx);
    for (int i = total - n_new; i < total; i++) {
        if (i < 0) {
            continue;
        }
        const char *segment = whisper_full_get_segment_text(ctx, i);
        if (segment != nullptr) {
            sink->text += segment;
        }
    }
    // Sent whole rather than as a delta: the Java side then has nothing to assemble and no way to
    // end up showing a sentence with a hole in it if one call is dropped.
    jstring text = sink->env->NewStringUTF(sink->text.c_str());
    if (text == nullptr) {
        return;
    }
    sink->env->CallVoidMethod(sink->listener, sink->method, text);
    sink->env->DeleteLocalRef(text);
    if (sink->env->ExceptionCheck()) {
        // A listener that threw must not take whisper down mid-decode; the final result still
        // arrives through the return value.
        sink->env->ExceptionClear();
    }
}

// pcm must be mono 16 kHz float samples in [-1, 1]; the Java side is responsible for the
// conversion, which it does with the decoder that already ships in this app.
//
// listener may be null. When it is not, it receives the transcript so far each time a segment is
// decoded - a minute of audio otherwise means a minute of staring at a spinner.
JNIEXPORT jstring JNICALL
Java_org_telegram_messenger_PrimeWhisper_nativeTranscribe(JNIEnv *env, jclass clazz, jlong ptr,
                                                          jfloatArray pcm, jstring language,
                                                          jint threads, jobject listener) {
    if (ptr == 0 || pcm == nullptr) {
        return nullptr;
    }
    whisper_context *ctx = reinterpret_cast<whisper_context *>(ptr);

    const jsize sampleCount = env->GetArrayLength(pcm);
    if (sampleCount <= 0) {
        return nullptr;
    }
    jfloat *samples = env->GetFloatArrayElements(pcm, nullptr);
    if (samples == nullptr) {
        return nullptr;
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_realtime = false;
    wparams.print_progress = false;
    wparams.print_timestamps = false;
    wparams.print_special = false;
    wparams.translate = false;
    wparams.no_context = true;
    wparams.single_segment = false;
    wparams.n_threads = threads > 0 ? threads : 4;
    // Voice messages are short and conversational; suppressing the non-speech tokens keeps
    // "(музыка)"-style artefacts out of what we show as a transcript.
    wparams.suppress_nst = true;

    std::string lang;
    if (language != nullptr) {
        const char *raw = env->GetStringUTFChars(language, nullptr);
        if (raw != nullptr) {
            lang = raw;
            env->ReleaseStringUTFChars(language, raw);
        }
    }
    // An empty language means auto-detect, which is what whisper's "auto" does.
    wparams.language = lang.empty() ? "auto" : lang.c_str();
    wparams.detect_language = false;

    segment_sink sink{env, nullptr, nullptr, std::string()};
    if (listener != nullptr) {
        jclass listenerClass = env->GetObjectClass(listener);
        if (listenerClass != nullptr) {
            sink.method = env->GetMethodID(listenerClass, "onSegment", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(listenerClass);
        }
        if (sink.method != nullptr) {
            sink.listener = listener;
            wparams.new_segment_callback = on_new_segment;
            wparams.new_segment_callback_user_data = &sink;
        } else {
            // Not fatal: without the callback this simply behaves as it did before, returning
            // everything at the end.
            env->ExceptionClear();
            LOGE("listener has no onSegment(String)");
        }
    }

    const int result = whisper_full(ctx, wparams, samples, sampleCount);
    env->ReleaseFloatArrayElements(pcm, samples, JNI_ABORT);
    if (result != 0) {
        LOGE("whisper_full failed: %d", result);
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; i++) {
        const char *segment = whisper_full_get_segment_text(ctx, i);
        if (segment != nullptr) {
            text += segment;
        }
    }
    // whisper puts a leading space on every segment; the UI wants a clean string.
    size_t start = text.find_first_not_of(" \t\n\r");
    size_t end = text.find_last_not_of(" \t\n\r");
    if (start == std::string::npos) {
        text.clear();
    } else {
        text = text.substr(start, end - start + 1);
    }
    return env->NewStringUTF(text.c_str());
}

}
