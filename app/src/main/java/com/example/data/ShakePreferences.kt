package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ShakeSettings
import com.example.model.WakeStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ShakePreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ShakeSettings> = _settings.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = loadSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun getSettings(): ShakeSettings = loadSettings()

    private fun loadSettings(): ShakeSettings {
        val wakeStyleName = prefs.getString(KEY_WAKE_STYLE, WakeStyle.INSTANT.name) ?: WakeStyle.INSTANT.name
        val wakeStyle = try {
            WakeStyle.valueOf(wakeStyleName)
        } catch (_: Exception) {
            WakeStyle.INSTANT
        }

        return ShakeSettings(
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, true),
            threshold = prefs.getFloat(KEY_THRESHOLD, 18.0f),
            shakesRequired = prefs.getInt(KEY_SHAKES_REQUIRED, 2),
            vibrateOnWake = prefs.getBoolean(KEY_VIBRATE_ON_WAKE, true),
            pocketMode = prefs.getBoolean(KEY_POCKET_MODE, true),
            screenOffOnly = prefs.getBoolean(KEY_SCREEN_OFF_ONLY, true),
            wakeStyle = wakeStyle,
            cooldownSeconds = prefs.getFloat(KEY_COOLDOWN, 1.5f),
            startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, true),
            showNotificationControls = prefs.getBoolean(KEY_SHOW_NOTIFICATION_CONTROLS, true),
            floatingControlsEnabled = prefs.getBoolean(KEY_FLOATING_CONTROLS_ENABLED, false),
            floatingControlsAlpha = prefs.getFloat(KEY_FLOATING_CONTROLS_ALPHA, 0.85f),
            totalWakesCount = prefs.getInt(KEY_TOTAL_WAKES, 0),
            lastWakeTimestamp = prefs.getLong(KEY_LAST_WAKE, 0L)
        )
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
    }

    fun setThreshold(threshold: Float) {
        prefs.edit().putFloat(KEY_THRESHOLD, threshold).apply()
    }

    fun setShakesRequired(count: Int) {
        prefs.edit().putInt(KEY_SHAKES_REQUIRED, count).apply()
    }

    fun setVibrateOnWake(vibrate: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE_ON_WAKE, vibrate).apply()
    }

    fun setPocketMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POCKET_MODE, enabled).apply()
    }

    fun setScreenOffOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_OFF_ONLY, enabled).apply()
    }

    fun setWakeStyle(style: WakeStyle) {
        prefs.edit().putString(KEY_WAKE_STYLE, style.name).apply()
    }

    fun setCooldownSeconds(seconds: Float) {
        prefs.edit().putFloat(KEY_COOLDOWN, seconds).apply()
    }

    fun setStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()
    }

    fun setShowNotificationControls(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NOTIFICATION_CONTROLS, enabled).apply()
    }

    fun setFloatingControlsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_CONTROLS_ENABLED, enabled).apply()
    }

    fun setFloatingControlsAlpha(alpha: Float) {
        prefs.edit().putFloat(KEY_FLOATING_CONTROLS_ALPHA, alpha).apply()
    }

    fun recordWakeEvent() {
        val count = prefs.getInt(KEY_TOTAL_WAKES, 0) + 1
        val now = System.currentTimeMillis()
        prefs.edit()
            .putInt(KEY_TOTAL_WAKES, count)
            .putLong(KEY_LAST_WAKE, now)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "shakewake_preferences"
        private const val KEY_IS_ENABLED = "key_is_enabled"
        private const val KEY_THRESHOLD = "key_threshold"
        private const val KEY_SHAKES_REQUIRED = "key_shakes_required"
        private const val KEY_VIBRATE_ON_WAKE = "key_vibrate_on_wake"
        private const val KEY_POCKET_MODE = "key_pocket_mode"
        private const val KEY_SCREEN_OFF_ONLY = "key_screen_off_only"
        private const val KEY_WAKE_STYLE = "key_wake_style"
        private const val KEY_COOLDOWN = "key_cooldown"
        private const val KEY_START_ON_BOOT = "key_start_on_boot"
        private const val KEY_SHOW_NOTIFICATION_CONTROLS = "key_show_notification_controls"
        private const val KEY_FLOATING_CONTROLS_ENABLED = "key_floating_controls_enabled"
        private const val KEY_FLOATING_CONTROLS_ALPHA = "key_floating_controls_alpha"
        private const val KEY_TOTAL_WAKES = "key_total_wakes"
        private const val KEY_LAST_WAKE = "key_last_wake"

        @Volatile
        private var instance: ShakePreferences? = null

        fun getInstance(context: Context): ShakePreferences {
            return instance ?: synchronized(this) {
                instance ?: ShakePreferences(context.applicationContext).also { instance = it }
            }
        }
    }
}
