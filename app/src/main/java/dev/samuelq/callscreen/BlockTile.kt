package dev.samuelq.callscreen

import android.graphics.drawable.Icon
import android.os.UserManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

// Quick Settings tile that flips the same switch as the app.
class BlockTile : TileService() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onStartListening() = update()

    // Not called while the tile is unavailable, so setup is always complete here.
    override fun onClick() {
        prefs.edit().putBoolean(KEY_ENABLED, !prefs.getBoolean(KEY_ENABLED, true)).apply()
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        // Before the first unlock after boot, BlockService can't run (it isn't direct boot aware)
        // and prefs can't be read, so grey the tile out. It's direct boot aware only so it can say
        // so; the saved choice is untouched and shows again the next time the shade opens.
        val unlocked = getSystemService(UserManager::class.java).isUserUnlocked
        val ready = unlocked && hasContacts() && hasRole()
        val blocking = ready && prefs.getBoolean(KEY_ENABLED, true)
        tile.state = when {
            !ready -> Tile.STATE_UNAVAILABLE // greyed out until unlocked and permissions are granted
            blocking -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.icon = Icon.createWithResource(this, if (blocking) R.drawable.ic_tile else R.drawable.ic_tile_off)
        tile.updateTile()
    }
}
