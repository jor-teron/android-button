package com.example.util

import android.content.Context
import android.media.AudioManager
import android.os.Build

object AudioHelper {

    fun volumeUp(context: Context, streamType: Int = AudioManager.STREAM_MUSIC) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.adjustStreamVolume(
                streamType,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
            )
        } catch (_: Exception) {}
    }

    fun volumeDown(context: Context, streamType: Int = AudioManager.STREAM_MUSIC) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.adjustStreamVolume(
                streamType,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_PLAY_SOUND
            )
        } catch (_: Exception) {}
    }

    fun toggleMute(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val isCurrentlyMuted = audioManager.isStreamMute(streamType)
                if (isCurrentlyMuted) {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    false
                } else {
                    audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    true
                }
            } else {
                audioManager.adjustStreamVolume(streamType, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getStreamVolume(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 0
        return try {
            audioManager.getStreamVolume(streamType)
        } catch (_: Exception) {
            0
        }
    }

    fun getMaxStreamVolume(context: Context, streamType: Int = AudioManager.STREAM_MUSIC): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 15
        return try {
            audioManager.getStreamMaxVolume(streamType)
        } catch (_: Exception) {
            15
        }
    }

    fun setStreamVolume(context: Context, streamType: Int, volume: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.setStreamVolume(streamType, volume, AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {}
    }
}
