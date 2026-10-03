package com.example.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.data.ShakePreferences

@RequiresApi(Build.VERSION_CODES.N)
class ShakeWakeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = ShakePreferences.getInstance(this)
        val isCurrentlyEnabled = prefs.getSettings().isEnabled
        val newStatus = !isCurrentlyEnabled
        prefs.setEnabled(newStatus)

        if (newStatus) {
            ShakeWakeService.start(this)
        } else {
            ShakeWakeService.stop(this)
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = ShakePreferences.getInstance(this)
        val isEnabled = prefs.getSettings().isEnabled

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Shake to Wake"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "Active" else "Disabled"
        }
        tile.updateTile()
    }
}
