package dev.samuelq.callscreen

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import java.text.DateFormat

const val PREFS = "prefs"
const val KEY_ENABLED = "enabled"
const val KEY_INSTANT_DECLINE = "instant_decline"
const val KEY_REPEAT_CALLERS = "repeat_callers"
const val KEY_BLOCKED_COUNT = "blocked_count"
const val KEY_FIRST_BLOCKED = "first_blocked"
const val KEY_LAST_BLOCKED = "last_blocked"
const val KEY_LAST_HIDDEN = "last_hidden"
const val KEY_NOTIFY = "notify"
const val PREFS_RECENT = "recent_blocked"

private const val REQ_ROLE = 1
private const val REQ_NOTIFY = 2

// Shared by the app and the Quick Settings tile. Turning off blocking
// or repeat callers forgets all recently blocked numbers.
fun Context.saveSwitch(key: String, on: Boolean) {
    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    if (!on && (key == KEY_ENABLED || key == KEY_REPEAT_CALLERS)) {
        getSharedPreferences(PREFS_RECENT, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

fun Context.hasRole() =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

class MainActivity : Activity() {
    private lateinit var toggle: Switch
    private lateinit var notify: Switch
    private lateinit var instantDecline: Switch
    private lateinit var repeatCallers: Switch
    private lateinit var statsTotal: TextView
    private lateinit var statsRecent: TextView
    private lateinit var options: View
    private lateinit var permissions: View
    private lateinit var rowContacts: View
    private lateinit var rowRole: View
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    // The tile and BlockService can change prefs while this screen is visible (opening the shade
    // doesn't always pause the activity). Kept in a field because prefs only holds listeners weakly.
    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Edge-to-edge enforced on API 35
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                insets
            }
        }

        toggle = findViewById(R.id.toggle)
        notify = findViewById(R.id.notify)
        instantDecline = findViewById(R.id.instant_decline)
        repeatCallers = findViewById(R.id.repeat_callers)
        statsTotal = findViewById(R.id.stats_total)
        statsRecent = findViewById(R.id.stats_recent)
        options = findViewById(R.id.options)
        permissions = findViewById(R.id.permissions)
        rowRole = findViewById(R.id.row_role)

        findViewById<Button>(R.id.open_default_apps).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            findViewById<Button>(R.id.add_tile).apply {
                visibility = View.VISIBLE
                setOnClickListener { requestAddTile() }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsChanged)

        // Skipped when the activity is only being recreated (e.g. rotation)
        if (savedInstanceState == null && !hasRole()) {
            val intent = getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            startActivityForResult(intent, REQ_ROLE)
        }
    }

    // Turning notifications on asks for permission first, and is only saved once it's granted.
    private fun setNotify(on: Boolean) {
        when {
            !on || canNotify() -> saveSwitch(KEY_NOTIFY, on)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
            else -> notificationsBlocked()
        }
        refresh() // undo the switch's own change until it's actually allowed
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode != REQ_NOTIFY) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            saveSwitch(KEY_NOTIFY, true)
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            notificationsBlocked()
        }
    }

    private fun notificationsBlocked() =
        Toast.makeText(this, R.string.notify_blocked_toast, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged)
        super.onDestroy()
    }

    // Android 13+ can offer to add the tile directly.
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, BlockTile::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                Toast.makeText(this, R.string.tile_already_added, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh() // also runs after the permission/role dialogs close, and after visiting Settings
    }

    private fun refresh() {
        val hasRole = hasRole()

        // Show off whenever blocking cannot actually run, without overwriting the saved choice.
        val enabled = hasRole && prefs.getBoolean(KEY_ENABLED, true)
        bind(toggle, KEY_ENABLED, enabled, hasRole)
        bind(notify, prefs.getBoolean(KEY_NOTIFY, false) && canNotify(), enabled, ::setNotify)
        bind(instantDecline, KEY_INSTANT_DECLINE, prefs.getBoolean(KEY_INSTANT_DECLINE, true), enabled)
        bind(repeatCallers, KEY_REPEAT_CALLERS, prefs.getBoolean(KEY_REPEAT_CALLERS, true), enabled)

        // Counted by BlockService; this screen updates live through prefsChanged.
        val count = prefs.getInt(KEY_BLOCKED_COUNT, 0)
        if (count == 0) {
            statsTotal.text = "0"
            statsRecent.setText(R.string.stats_none)
        } else {
            val since = DateUtils.formatDateTime(
                this, prefs.getLong(KEY_FIRST_BLOCKED, 0), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
            statsTotal.text = getString(R.string.stats_total_value, count, since)

            // Just the time if it was today, otherwise the date.
            val last = DateUtils.formatSameDayTime(
                prefs.getLong(KEY_LAST_BLOCKED, 0), System.currentTimeMillis(), DateFormat.MEDIUM, DateFormat.SHORT)
            statsRecent.text = if (prefs.getBoolean(KEY_LAST_HIDDEN, false)) {
                getString(R.string.stats_recent_hidden, last)
            } else last
        }

        options.showIf(hasRole)
        permissions.showIf(!hasRole)
        rowRole.showIf(!hasRole)
    }

    // Detach the listener while setting state so only real taps are persisted.
    private fun bind(view: Switch, key: String, checked: Boolean, enabled: Boolean) =
        bind(view, checked, enabled) { saveSwitch(key, it) }

    private fun bind(view: Switch, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
        view.setOnCheckedChangeListener(null)
        if (view.isChecked != checked) view.isChecked = checked
        view.setOnCheckedChangeListener { _, on -> onChange(on) }
        if (view.isEnabled != enabled) {
            view.isEnabled = enabled
            view.jumpDrawablesToCurrentState()
        }
    }

    private fun View.showIf(show: Boolean) {
        visibility = if (show) View.VISIBLE else View.GONE
    }
}
