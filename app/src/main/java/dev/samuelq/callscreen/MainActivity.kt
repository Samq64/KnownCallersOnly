package dev.samuelq.callscreen

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView

const val PREFS = "prefs"
const val KEY_ENABLED = "enabled"
const val KEY_TILE_ADDED = "tile_added"

// Blocking can only run with both of these; shared by the app and the Quick Settings tile.
fun Context.hasContacts() =
    checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
fun Context.hasRole() =
    getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

class MainActivity : Activity() {
    private lateinit var toggle: Switch
    private lateinit var statusContacts: TextView
    private lateinit var statusRole: TextView
    private lateinit var contactsBtn: Button
    private lateinit var roleBtn: Button
    private lateinit var addTileBtn: Button
    private val roles by lazy { getSystemService(RoleManager::class.java) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val saveToggle = CompoundButton.OnCheckedChangeListener { _, on ->
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }
    // The tile can change prefs while this screen is visible (opening the shade doesn't always
    // pause the activity). Kept in a field because prefs only holds listeners weakly.
    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE) // the layout has its own title
        setContentView(R.layout.activity_main)

        // Edge-to-edge (enforced on API 35): pad for system bars AND the display cutout,
        // so content clears the camera hole-punch in landscape. Below API 35 the system
        // already keeps content out of these areas, so the insets are zero there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(i.left, i.top, i.right, i.bottom)
                insets
            }
        }

        toggle = findViewById(R.id.toggle)
        statusContacts = findViewById(R.id.status_contacts)
        statusRole = findViewById(R.id.status_role)
        contactsBtn = findViewById(R.id.grant_contacts)
        roleBtn = findViewById(R.id.grant_role)
        addTileBtn = findViewById(R.id.add_tile)

        contactsBtn.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 0) }
        roleBtn.setOnClickListener {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addTileBtn.setOnClickListener { requestAddTile() }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsChanged)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsChanged)
        super.onDestroy()
    }

    // Android 13+ can offer to add the tile directly instead of making users edit Quick Settings.
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, BlockTile::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) {} // BlockTile records KEY_TILE_ADDED itself, which refreshes this screen
    }

    override fun onResume() {
        super.onResume()
        refresh() // also runs after the permission/role dialogs close, and after visiting Settings
    }

    private fun refresh() {
        val hasContacts = hasContacts()
        val hasRole = hasRole()
        val ready = hasContacts && hasRole

        // Show off whenever blocking cannot actually run, without overwriting the saved choice:
        // detach the listener so only real taps are persisted.
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = ready && prefs.getBoolean(KEY_ENABLED, true)
        toggle.setOnCheckedChangeListener(saveToggle)
        toggle.isEnabled = ready
        showStatus(statusContacts, hasContacts, R.string.perm_contacts)
        showStatus(statusRole, hasRole, R.string.perm_role)
        contactsBtn.visibility = if (hasContacts) View.GONE else View.VISIBLE
        roleBtn.visibility = if (hasRole) View.GONE else View.VISIBLE
        val canAddTile = ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !prefs.getBoolean(KEY_TILE_ADDED, false)
        addTileBtn.visibility = if (canAddTile) View.VISIBLE else View.GONE
    }

    // Icon + tint show the state visually; contentDescription makes TalkBack say it.
    private fun showStatus(view: TextView, ok: Boolean, label: Int) {
        val name = getString(label)
        view.text = name
        view.contentDescription = getString(if (ok) R.string.status_granted else R.string.status_missing, name)
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (ok) R.drawable.ic_granted else R.drawable.ic_missing, 0, 0, 0)
        view.compoundDrawableTintList =
            themeColor(if (ok) android.R.attr.colorAccent else android.R.attr.colorError)
    }

    private fun themeColor(attr: Int): ColorStateList {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) getColorStateList(tv.resourceId) else ColorStateList.valueOf(tv.data)
    }
}
