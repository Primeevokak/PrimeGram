#ifndef log_h
#define log_h

#include <android/log.h>
#include <jni.h>

#define LOG_TAG "tmessages_native"
#ifndef LOG_DISABLED
#ifndef LOGI
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#endif
#ifndef LOGD
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#endif
#ifndef LOGE
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#endif
#ifndef LOGV
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#endif
#else
#ifndef LOGI
#define LOGI(...)
#endif
#ifndef LOGD
#define LOGD(...)
#endif
#ifndef LOGE
#define LOGE(...)
#endif
#ifndef LOGV
#define LOGV(...)
#endif
#endif

#ifndef MAX
#define MAX(x, y) ((x) > (y)) ? (x) : (y)
#endif
#ifndef MIN
#define MIN(x, y) ((x) < (y)) ? (x) : (y)
#endif

#endif
