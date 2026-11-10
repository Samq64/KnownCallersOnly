package dev.samuelq.callscreen

import android.graphics.drawable.Icon
import android.os.UserManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class BlockTile : TileService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onStartListening() = update()

    override fun onClick() {
        saveSwitch(KEY_ENABLED, !prefs.getBoolean(KEY_ENABLED, true))
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        // Before the first unlock after boot, BlockService can't access contacts, so gray the tile out.
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        val ready = unlocked && hasRole()
        val blocking = ready && prefs.getBoolean(KEY_ENABLED, true)
        tile.state = when {
            !ready -> Tile.STATE_UNAVAILABLE
            blocking -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        // Say why the tile is unavailable; otherwise null leaves Android's own On/Off label.
        tile.subtitle = when {
            !unlocked -> getString(R.string.tile_locked)
            !ready -> getString(R.string.tile_setup)
            else -> null
        }
        tile.icon = Icon.createWithResource(this, if (blocking) R.drawable.ic_tile else R.drawable.ic_tile_off)
        tile.updateTile()
    }
}
