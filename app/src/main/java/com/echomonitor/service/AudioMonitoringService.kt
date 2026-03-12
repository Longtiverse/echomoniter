package com.echomonitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.echomonitor.MainActivity
import com.echomonitor.R
import com.echomonitor.audio.AudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 音频监控前台服务
 * 核心功能：实时收音和播放，后台保活
 */
class AudioMonitoringService : Service() {

    companion object {
        const val TAG = "AudioMonitoringService"
        const val CHANNEL_ID = "echo_monitor_channel"
        const val NOTIFICATION_ID = 1
        
        const val ACTION_START = "com.echomonitor.ACTION_START"
        const val ACTION_STOP = "com.echomonitor.ACTION_STOP"
        const val ACTION_UPDATE_VOLUME = "com.echomonitor.ACTION_UPDATE_VOLUME"
        const val EXTRA_VOLUME = "extra_volume"
        
        @Volatile
        var isRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private lateinit var audioEngine: AudioEngine
    private lateinit var audioManager: AudioManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentVolume = 1.0f

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        
        audioEngine = AudioEngine()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        createNotificationChannel()
        registerHeadsetReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
            ACTION_UPDATE_VOLUME -> {
                val volume = intent.getFloatExtra(EXTRA_VOLUME, 1.0f)
                updateVolume(volume)
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopMonitoring()
        unregisterHeadsetReceiver()
        releaseWakeLock()
        abandonAudioFocus()
    }

    /**
     * 开始音频监控
     */
    private fun startMonitoring() {
        if (isRunning) return
        
        Log.d(TAG, "Starting audio monitoring...")
        
        // 1. 请求音频焦点
        requestAudioFocus()
        
        // 2. 获取WakeLock防止CPU休眠
        acquireWakeLock()
        
        // 3. 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 4. 初始化并启动音频引擎
        serviceScope.launch {
            try {
                audioEngine.initialize()
                audioEngine.setVolume(currentVolume)
                audioEngine.start()
                isRunning = true
                Log.d(TAG, "Audio monitoring started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start audio monitoring", e)
                stopSelf()
            }
        }
    }

    /**
     * 停止音频监控
     */
    private fun stopMonitoring() {
        if (!isRunning) return
        
        Log.d(TAG, "Stopping audio monitoring...")
        
        isRunning = false
        
        // 停止音频引擎
        audioEngine.stop()
        audioEngine.release()
        
        // 释放资源
        releaseWakeLock()
        abandonAudioFocus()
        
        // 停止前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        Log.d(TAG, "Audio monitoring stopped")
    }

    /**
     * 更新播放音量
     */
    private fun updateVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        if (isRunning) {
            audioEngine.setVolume(currentVolume)
        }
    }

    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            Log.d(TAG, "Audio focus gained")
                            if (isRunning) audioEngine.start()
                        }
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            Log.d(TAG, "Audio focus lost")
                            // 继续播放，因为我们设置了MAY_DUCK
                        }
                    }
                }
                .build()
            
            audioFocusRequest = focusRequest
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(it)
            }
        }
        audioFocusRequest = null
    }

    /**
     * 获取WakeLock
     */
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EchoMonitor::AudioMonitoringWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10*60*1000L) // 10分钟
        }
    }

    /**
     * 释放WakeLock
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
 
