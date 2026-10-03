package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.ShakePreferences
import com.example.service.ShakeWakeService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = ShakePreferences.getInstance(context)
            val settings = prefs.getSettings()
            if (settings.startOnBoot && settings.isEnabled) {
                ShakeWakeService.start(context)
            }
        }
    }
}
