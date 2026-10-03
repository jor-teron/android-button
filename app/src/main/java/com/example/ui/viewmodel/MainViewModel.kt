package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WakeActivity
import com.example.data.ShakePreferences
import com.example.model.ShakePreset
import com.example.model.ShakeSettings
import com.example.model.WakeStyle
import com.example.service.FloatingControlsService
import com.example.service.LiveSensorReading
import com.example.service.ShakeWakeService
import com.example.util.AudioHelper
import com.example.util.VibratorHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VolumeState(
    val mediaVolume: Int = 8,
    val maxMediaVolume: Int = 15,
    val ringVolume: Int = 5,
    val maxRingVolume: Int = 7,
    val isMediaMuted: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = ShakePreferences.getInstance(application)
    val settings: StateFlow<ShakeSettings> = prefs.settings

    val isServiceRunning: StateFlow<Boolean> = ShakeWakeService.isServiceRunning
    val liveSensorData: StateFlow<LiveSensorReading> = ShakeWakeService.liveSensorData

    private val _volumeState = MutableStateFlow(loadVolumeState())
    val volumeState: StateFlow<VolumeState> = _volumeState.asStateFlow()

    private val _recentWakeAnimation = MutableStateFlow(false)
    val recentWakeAnimation: StateFlow<Boolean> = _recentWakeAnimation.asStateFlow()

    init {
        // Collect wake events from service
        viewModelScope.launch {
            ShakeWakeService.wakeEvents.collect {
                _recentWakeAnimation.value = true
                delay(1200)
                _recentWakeAnimation.value = false
            }
        }

        // Auto-start service if enabled in preferences
        if (settings.value.isEnabled) {
            startService()
        }
    }

    fun refreshVolume() {
        _volumeState.value = loadVolumeState()
    }

    private fun loadVolumeState(): VolumeState {
        val context = getApplication<Application>()
        val mediaVol = AudioHelper.getStreamVolume(context, AudioManager.STREAM_MUSIC)
        val maxMediaVol = AudioHelper.getMaxStreamVolume(context, AudioManager.STREAM_MUSIC)
        val ringVol = AudioHelper.getStreamVolume(context, AudioManager.STREAM_RING)
        val maxRingVol = AudioHelper.getMaxStreamVolume(context, AudioManager.STREAM_RING)

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && am != null) {
            am.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            mediaVol == 0
        }

        return VolumeState(
            mediaVolume = mediaVol,
            maxMediaVolume = maxMediaVol,
            ringVolume = ringVol,
            maxRingVolume = maxRingVol,
            isMediaMuted = isMuted
        )
    }

    fun toggleService(enabled: Boolean) {
        prefs.setEnabled(enabled)
        if (enabled) {
            startService()
            VibratorHelper.vibrateSuccess(getApplication())
        } else {
            stopService()
            VibratorHelper.vibrateClick(getApplication())
        }
    }

    fun startService() {
        ShakeWakeService.start(getApplication())
    }

    fun stopService() {
        ShakeWakeService.stop(getApplication())
    }

    fun updateThreshold(threshold: Float) {
        prefs.setThreshold(threshold)
    }

    fun applyPreset(preset: ShakePreset) {
        prefs.setThreshold(preset.threshold)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun updateShakesRequired(count: Int) {
        prefs.setShakesRequired(count)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun togglePocketMode(enabled: Boolean) {
        prefs.setPocketMode(enabled)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun toggleVibrateOnWake(enabled: Boolean) {
        prefs.setVibrateOnWake(enabled)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun toggleScreenOffOnly(enabled: Boolean) {
        prefs.setScreenOffOnly(enabled)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun updateWakeStyle(style: WakeStyle) {
        prefs.setWakeStyle(style)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun toggleStartOnBoot(enabled: Boolean) {
        prefs.setStartOnBoot(enabled)
        VibratorHelper.vibrateClick(getApplication())
    }

    fun toggleNotificationControls(enabled: Boolean) {
        prefs.setShowNotificationControls(enabled)
        // Restart notification if service running
        if (isServiceRunning.value) {
            startService()
        }
    }

    fun toggleFloatingControls(enabled: Boolean, context: Context) {
        if (enabled) {
            if (FloatingControlsService.hasOverlayPermission(context)) {
                prefs.setFloatingControlsEnabled(true)
                FloatingControlsService.start(context)
                VibratorHelper.vibrateSuccess(context)
            } else {
                requestOverlayPermission(context)
            }
        } else {
            prefs.setFloatingControlsEnabled(false)
            FloatingControlsService.stop(context)
            VibratorHelper.vibrateClick(context)
        }
    }

    fun updateFloatingAlpha(alpha: Float, context: Context) {
        prefs.setFloatingControlsAlpha(alpha)
        if (settings.value.floatingControlsEnabled) {
            FloatingControlsService.stop(context)
            FloatingControlsService.start(context)
        }
    }

    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun requestBatteryOptimizationExemption(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }
    }

    // Audio Controls
    fun volumeUp() {
        val context = getApplication<Application>()
        AudioHelper.volumeUp(context, AudioManager.STREAM_MUSIC)
        VibratorHelper.vibrateClick(context)
        refreshVolume()
    }

    fun volumeDown() {
        val context = getApplication<Application>()
        AudioHelper.volumeDown(context, AudioManager.STREAM_MUSIC)
        VibratorHelper.vibrateClick(context)
        refreshVolume()
    }

    fun toggleMute() {
        val context = getApplication<Application>()
        AudioHelper.toggleMute(context, AudioManager.STREAM_MUSIC)
        VibratorHelper.vibrateClick(context)
        refreshVolume()
    }

    fun setMediaVolume(newVolume: Int) {
        val context = getApplication<Application>()
        AudioHelper.setStreamVolume(context, AudioManager.STREAM_MUSIC, newVolume)
        refreshVolume()
    }

    fun testScreenWake(context: Context) {
        VibratorHelper.vibrateWake(context)
        val intent = Intent(context, WakeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
}
