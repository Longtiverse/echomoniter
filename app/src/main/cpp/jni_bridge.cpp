#include <jni.h>
#include "audio_engine.h"
#include <android/log.h>
#include <memory>

#define LOG_TAG "JNI_Bridge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 全局音频引擎实例
static std::unique_ptr<AudioEngine> audioEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeInitialize(JNIEnv* env, jobject thiz) {
    LOGD("nativeInitialize called");
    
    if (!audioEngine) {
        audioEngine = std::make_unique<AudioEngine>();
    }
    
    return audioEngine->initialize() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeStart(JNIEnv* env, jobject thiz) {
    LOGD("nativeStart called");
    if (audioEngine) {
        audioEngine->start();
    }
}

JNIEXPORT void JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeStop(JNIEnv* env, jobject thiz) {
    LOGD("nativeStop called");
    if (audioEngine) {
        audioEngine->stop();
    }
}

JNIEXPORT void JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeRelease(JNIEnv* env, jobject thiz) {
    LOGD("nativeRelease called");
    if (audioEngine) {
        audioEngine->release();
        audioEngine.reset();
    }
}

JNIEXPORT void JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeSetVolume(JNIEnv* env, jobject thiz, jfloat volume) {
    if (audioEngine) {
        audioEngine->setVolume(volume);
    }
}

JNIEXPORT void JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeSetMaxOutputLevel(JNIEnv* env, jobject thiz, jfloat maxDb) {
    if (audioEngine) {
        audioEngine->setMaxOutputLevel(maxDb);
    }
}

JNIEXPORT jdouble JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeGetLatencyMs(JNIEnv* env, jobject thiz) {
    if (audioEngine) {
        return audioEngine->getLatencyMs();
    }
    return 0.0;
}

JNIEXPORT jintArray JNICALL
Java_com_echomonitor_audio_AudioEngine_nativeGetBufferState(JNIEnv* env, jobject thiz) {
    jintArray result = env->NewIntArray(4);
    if (audioEngine) {
        int bufferSize, capacity, writeIdx, readIdx;
        audioEngine->getBufferState(&bufferSize, &capacity, &writeIdx, &readIdx);
        
        jint state[] = {bufferSize, capacity, writeIdx, readIdx};
        env->SetIntArrayRegion(result, 0, 4, state);
    }
    return result;
}

} // extern "C"
