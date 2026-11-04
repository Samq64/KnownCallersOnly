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
import android.view.Window
import android.view.WindowInsets
import android.widget.Button
import android.widget.CompoundButton
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
const val KEY_LAST_BLOCKED = "last_blocked"
const val PREFS_RECENT = "recent_blocked"

private const val REQ_ROLE = 1

// Shared by the app and the Quick Settings tile. Turning off blocking
// or repeat callers forgets all recently blocked numbers.
fun Context.saveSwitch(key: String, on: Boolean) {
    getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, on).apply()
    if (!on && (key == KEY_ENABLED || key == KEY_REPEAT_CALLERS)) {
        getSharedPreferences(PREFS_RECENT, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

fun Context.hasContacts() =
    checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
fun Context.hasRole() =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

class MainActivity : Activity() {
    private lateinit var toggle: Switch
    private lateinit var instantDecline: Switch
    private lateinit var repeatCallers: Switch
    private lateinit var blockedCount: TextView
    private lateinit var options: View
    private lateinit var permissions: View
    private lateinit var rowContacts: View
    private lateinit var rowRole: View
    private val roles by lazy { getSystemService(RoleManager::class.java) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private fun saver(key: String) = CompoundButton.OnCheckedChangeListener { _, on ->
        saveSwitch(key, on)
    }
    // The tile and BlockService can change prefs while this screen is visible (opening the shade
    // doesn't always pause the activity). Kept in a field because prefs only holds listeners weakly.
    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
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
        instantDecline = findViewById(R.id.instant_decline)
        repeatCallers = findViewById(R.id.repeat_callers)
        blockedCount = findViewById(R.id.blocked_count)
        options = findViewById(R.id.options)
        permissions = findViewById(R.id.permissions)
        rowContacts = findViewById(R.id.row_contacts)
        rowRole = findViewById(R.id.row_role)

        findViewById<Button>(R.id.open_app_info).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
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

        // Ask for whatever is missing straight away, one dialog after the other.
        // Skipped when the activity is only being recreated (e.g. rotation)
        if (savedInstanceState == null) {
            if (!hasRole()) startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE)
            else if (!hasContacts()) requestContacts()
        }
    }

    private fun requestContacts() = requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 0)

    // Whatever the user chose for the role, move on to the contacts prompt.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ROLE && !hasContacts()) requestContacts()
    }

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
        val hasContacts = hasContacts()
        val hasRole = hasRole()
        val ready = hasContacts && hasRole

        // Show off whenever blocking cannot actually run, without overwriting the saved choice.
        val enabled = ready && prefs.getBoolean(KEY_ENABLED, true)
        bind(toggle, KEY_ENABLED, enabled, ready)
        bind(instantDecline, KEY_INSTANT_DECLINE, prefs.getBoolean(KEY_INSTANT_DECLINE, true), enabled)
        bind(repeatCallers, KEY_REPEAT_CALLERS, prefs.getBoolean(KEY_REPEAT_CALLERS, true), enabled)

        // Counted by BlockService; this screen updates live through prefsChanged.
        val count = prefs.getInt(KEY_BLOCKED_COUNT, 0)
        blockedCount.showIf(count > 0)
        if (count > 0) {
            // Just the time if it was today, otherwise the date.
            val last = DateUtils.formatSameDayTime(
                prefs.getLong(KEY_LAST_BLOCKED, 0), System.currentTimeMillis(), DateFormat.MEDIUM, DateFormat.SHORT)
            blockedCount.text = resources.getQuantityString(R.plurals.blocked_count, count, count, last)
        }

        options.showIf(ready)
        permissions.showIf(!ready)
        rowContacts.showIf(!hasContacts)
        rowRole.showIf(!hasRole)
    }

    // Detach the listener while setting state so only real taps are persisted.
    private fun bind(view: Switch, key: String, checked: Boolean, enabled: Boolean) {
        view.setOnCheckedChangeListener(null)
        if (view.isChecked != checked) view.isChecked = checked
        view.setOnCheckedChangeListener(saver(key))
        if (view.isEnabled != enabled) {
            view.isEnabled = enabled
            view.jumpDrawablesToCurrentState()
        }
    }

    private fun View.showIf(show: Boolean) {
        visibility = if (show) View.VISIBLE else View.GONE
    }
}
