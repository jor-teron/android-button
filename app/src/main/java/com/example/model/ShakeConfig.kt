package com.example.model

enum class ShakePreset(val label: String, val threshold: Float, val description: String) {
    GENTLE("Gentle", 13.0f, "Easy to trigger, best for light table shakes"),
    BALANCED("Balanced", 18.0f, "Recommended: prevents walking false triggers"),
    FIRM("Firm", 24.0f, "Requires intentional, firm wrist shake"),
    STRONG("Strong", 30.0f, "Vigorous shake only, zero accidental wakes");

    companion object {
        fun fromThreshold(value: Float): ShakePreset? {
            return entries.find { kotlin.math.abs(it.threshold - value) < 1.0f }
        }
    }
}

enum class WakeStyle(val label: String, val description: String) {
    INSTANT("Direct to Lock Screen", "Wakes screen immediately and returns to your system lock screen or home screen"),
    AMBIENT_HUD("Ambient Wake HUD", "Shows time, battery, and quick unlock/volume touch buttons on black OLED screen");
}

data class ShakeSettings(
    val isEnabled: Boolean = true,
    val threshold: Float = 18.0f,
    val shakesRequired: Int = 2,
    val vibrateOnWake: Boolean = true,
    val pocketMode: Boolean = true,
    val screenOffOnly: Boolean = true,
    val wakeStyle: WakeStyle = WakeStyle.INSTANT,
    val cooldownSeconds: Float = 1.5f,
    val startOnBoot: Boolean = true,
    val showNotificationControls: Boolean = true,
    val floatingControlsEnabled: Boolean = false,
    val floatingControlsAlpha: Float = 0.85f,
    val totalWakesCount: Int = 0,
    val lastWakeTimestamp: Long = 0L
)
