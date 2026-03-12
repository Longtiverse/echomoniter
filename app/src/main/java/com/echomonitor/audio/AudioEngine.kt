package com.echomonitor.audio

import android.util.Log
import kotlinx.coroutines.*

/**
 * 音频引擎 - 桥接Kotlin和C++ Oboe音频引擎
 * 管理音频捕获、播放和音量控制
 */
class AudioEngine {

    companion object {
        const val TAG = "AudioEngine"
        
        // 加载原生库
        init {
            System.loadLibrary("echo_audio")
        }
    }

    private var isInitialized = false
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 初始化音频引擎
     */
    fun initialize(): Boolean {
        if (isInitialized) return true
        
        return try {
            Log.d(TAG, "Initializing audio engine...")
            val result = nativeInitialize()
            isInitialized = result
            Log.d(TAG, "Audio engine initialized: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize audio engine", e)
            false
        }
    }

    /**
     * 开始音频捕获和播放
     */
    fun start() {
        if (!isInitialized || isRunning) return
        
        Log.d(TAG, "Starting audio engine...")
        nativeStart()
        isRunning = true
        
        // 启动延迟监控
        startLatencyMonitor()
    }

    /**
     * 停止音频捕获和播放
     */
    fun stop() {
        if (!isRunning) return
        
        Log.d(TAG, "Stopping audio engine...")
        nativeStop()
        isRunning = false
    }

    /**
     * 释放音频引擎资源
     */
    fun release() {
        stop()
        if (isInitialized) {
            Log.d(TAG, "Releasing audio engine...")
            nativeRelease()
            isInitialized = false
        }
        scope.cancel()
    }

    /**
     * 设置播放音量
     * @param volume 音量值 (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        if (!isInitialized) return
        val clampedVolume = volume.coerceIn(0f, 1f)
        nativeSetVolume(clampedVolume)
    }

    /**
     * 设置最大输出电平（dB）
     * @param maxDb 最大音量dB值（如 -3dB 表示最大音量为正常的70%）
     */
    fun setMaxOutputLevel(maxDb: Float) {
        if (!isInitialized) return
        nativeSetMaxOutputLevel(maxDb)
    }

    /**
     * 获取当前延迟（毫秒）
     */
    fun getCurrentLatencyMs(): Double {
        return if (isInitialized) nativeGetLatencyMs() else 0.0
    }

    /**
     * 获取缓冲区状态
     */
    fun getBufferState(): BufferState {
        return if (isInitialized) {
            val state = nativeGetBufferState()
            BufferState(
                bufferSize = state[0],
                bufferCapacity = state[1],
                writeIndex = state[2],
                readIndex = state[3]
            )
        } else {
            BufferState(0, 0, 0, 0)
        }
    }

    /**
     * 监控延迟
     */
    private fun startLatencyMonitor() {
        scope.launch {
            while (isRunning) {
                val latency = getCurrentLatencyMs()
                Log.v(TAG, "Current latency: ${latency}ms")
                delay(2000) // 每2秒检查一次
            }
        }
    }

    // 原生方法声明
    private external fun nativeInitialize(): Boolean
    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeRelease()
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeSetMaxOutputLevel(maxDb: Float)
    private external fun nativeGetLatencyMs(): Double
    private external fun nativeGetBufferState(): IntArray

    /**
     * 缓冲区状态数据类
     */
    data class BufferState(
        val bufferSize: Int,
        val bufferCapacity: Int,
        val writeIndex: Int,
        val readIndex: Int
    )
}
