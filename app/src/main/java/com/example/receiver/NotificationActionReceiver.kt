package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.WakeActivity
import com.example.data.ShakePreferences
import com.example.service.ShakeWakeService
import com.example.util.AudioHelper
import com.example.util.VibratorHelper

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_VOL_UP -> {
                AudioHelper.volumeUp(context)
                VibratorHelper.vibrateClick(context)
            }
            ACTION_VOL_DOWN -> {
                AudioHelper.volumeDown(context)
                VibratorHelper.vibrateClick(context)
            }
            ACTION_MUTE -> {
                AudioHelper.toggleMute(context)
                VibratorHelper.vibrateClick(context)
            }
            ACTION_WAKE_SCREEN -> {
                VibratorHelper.vibrateWake(context)
                val wakeIntent = Intent(context, WakeActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                }
                context.startActivity(wakeIntent)
            }
            ACTION_TOGGLE_SERVICE -> {
                val prefs = ShakePreferences.getInstance(context)
                val newStatus = !prefs.getSettings().isEnabled
                prefs.setEnabled(newStatus)
                if (newStatus) {
                    ShakeWakeService.start(context)
                } else {
                    ShakeWakeService.stop(context)
                }
                VibratorHelper.vibrateClick(context)
            }
        }
    }

    companion object {
        const val ACTION_VOL_UP = "com.example.ACTION_VOL_UP"
        const val ACTION_VOL_DOWN = "com.example.ACTION_VOL_DOWN"
        const val ACTION_MUTE = "com.example.ACTION_MUTE"
        const val ACTION_WAKE_SCREEN = "com.example.ACTION_WAKE_SCREEN"
        const val ACTION_TOGGLE_SERVICE = "com.example.ACTION_TOGGLE_SERVICE"
    }
}
