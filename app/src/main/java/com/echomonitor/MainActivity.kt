package com.echomonitor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.echomonitor.audio.AudioEngine
import com.echomonitor.audio.VolumeController
import com.echomonitor.databinding.ActivityMainBinding
import com.echomonitor.permission.PermissionManager
import com.echomonitor.service.AudioMonitoringService

/**
 * 主Activity - 应用主界面
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager
    private lateinit var volumeController: VolumeController

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResults(permissions)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = PermissionManager(this)
        volumeController = VolumeController(this)

        initUI()
        checkPermissions()
    }

    private fun initUI() {
        // 主开关
        binding.switchMonitor.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startMonitoring()
            } else {
                stopMonitoring()
            }
        }

        // 用户音量滑块
        binding.seekBarVolume.max = 100
        binding.seekBarVolume.progress = (volumeController.userVolume * 100).toInt()
        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumeController.userVolume = progress / 100f
                    binding.textVolumeValue.text = "$progress%"
                    updateServiceVolume()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 最大音量限制滑块
        binding.seekBarMaxVolume.max = 100
        binding.seekBarMaxVolume.progress = volumeController.getMaxVolumePercentage()
        binding.seekBarMaxVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volumeController.setMaxVolumePercentage(progress)
                    binding.textMaxVolumeValue.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 更新UI初始值
        updateUI()
    }

    private fun updateUI() {
        binding.textVolumeValue.text = "${(volumeController.userVolume * 100).toInt()}%"
        binding.textMaxVolumeValue.text = "${volumeController.getMaxVolumePercentage()}%"
        binding.switchMonitor.isChecked = AudioMonitoringService.isRunning
    }

    private fun checkPermissions() {
        if (!permissionManager.hasAllPermissions()) {
            requestPermissionLauncher.launch(PermissionManager.REQUIRED_PERMISSIONS)
        }
    }

    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "权限已获取", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要录音权限才能使用", Toast.LENGTH_LONG).show()
            binding.switchMonitor.isChecked = false
        }
    }

    private fun startMonitoring() {
        if (!permissionManager.hasAllPermissions()) {
            Toast.makeText(this, "请先授予权限", Toast.LENGTH_SHORT).show()
            binding.switchMonitor.isChecked = false
            return
        }

        val intent = Intent(this, AudioMonitoringService::class.java).apply {
            action = AudioMonitoringService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        
        Toast.makeText(this, "开始监听", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitoring() {
        val intent = Intent(this, AudioMonitoringService::class.java).apply {
            action = AudioMonitoringService.ACTION_STOP
        }
        startService(intent)
        
        Toast.makeText(this, "停止监听", Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceVolume() {
        if (AudioMonitoringService.isRunning) {
            val intent = Intent(this, AudioMonitoringService::class.java).apply {
                action = AudioMonitoringService.ACTION_UPDATE_VOLUME
                putExtra(AudioMonitoringService.EXTRA_VOLUME, volumeController.userVolume)
            }
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
