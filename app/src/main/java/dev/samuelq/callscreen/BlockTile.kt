package dev.samuelq.callscreen

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

// Quick Settings tile that flips the same switch as the app.
class BlockTile : TileService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onTileAdded() = setAdded(true)
    override fun onTileRemoved() = setAdded(false)

    override fun onStartListening() {
        setAdded(true) // covers tiles added before the app's data was cleared
        update()
    }

    // Not called while the tile is unavailable, so setup is always complete here.
    override fun onClick() {
        prefs.edit().putBoolean(KEY_ENABLED, !prefs.getBoolean(KEY_ENABLED, true)).apply()
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val ready = hasContacts() && hasRole()
        val blocking = ready && prefs.getBoolean(KEY_ENABLED, true)
        tile.state = when {
            !ready -> Tile.STATE_UNAVAILABLE // greyed out until permissions are granted
            blocking -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, if (blocking) R.drawable.ic_tile else R.drawable.ic_tile_off)
        tile.updateTile()
    }

    // No-op writes are skipped by SharedPreferences, so no listeners fire when nothing changed.
    private fun setAdded(added: Boolean) = prefs.edit().putBoolean(KEY_TILE_ADDED, added).apply()
}
