package dev.samuelq.callscreen

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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

class MainActivity : Activity() {
    private lateinit var toggle: Switch
    private lateinit var statusContacts: TextView
    private lateinit var statusRole: TextView
    private lateinit var contactsBtn: Button
    private lateinit var roleBtn: Button
    private val roles by lazy { getSystemService(RoleManager::class.java) }
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val saveToggle = CompoundButton.OnCheckedChangeListener { _, on ->
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

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

        contactsBtn.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 0) }
        roleBtn.setOnClickListener {
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh() // also runs after the permission/role dialogs close, and after visiting Settings
    }

    private fun refresh() {
        val hasContacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasRole = roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
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
