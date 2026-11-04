package dev.samuelq.callscreen

import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils

// Numbers of recently blocked calls, kept only as long as the repeat window needs them.
private const val PREFS_RECENT = "recent_blocked"
private const val REPEAT_WINDOW_MS = 15 * 60 * 1000L

class BlockService : CallScreeningService() {
    private val recent by lazy { getSharedPreferences(PREFS_RECENT, MODE_PRIVATE) }

    override fun onScreenCall(details: Call.Details) {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val allowRepeats = prefs.getBoolean(KEY_REPEAT_CALLERS, true)
        forgetOldNumbers(allowRepeats, now)
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        val number = details.handle?.schemeSpecificPart
        val block = enabled &&
            details.callDirection == Call.Details.DIRECTION_INCOMING &&
            (number.isNullOrBlank() || !isContact(number)) &&
            !(allowRepeats && isRepeatCall(number, now))
        if (block) {
            prefs.edit()
                .putInt(KEY_BLOCKED_COUNT, prefs.getInt(KEY_BLOCKED_COUNT, 0) + 1)
                .putLong(KEY_LAST_BLOCKED, now)
                .apply()
        }
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(block)
                // Reject: caller gets busy/voicemail right away. Otherwise the call is hidden
                // here but keeps ringing for the caller until the carrier's no-answer timeout.
                .setRejectCall(block && prefs.getBoolean(KEY_INSTANT_DECLINE, true))
                .setSkipNotification(block)
                .build()
        )
    }

    // Drop numbers once the window has passed (all of them if the option is off), so none are
    // kept longer than needed. Runs on every incoming call.
    private fun forgetOldNumbers(allowRepeats: Boolean, now: Long) {
        val edit = recent.edit()
        for ((key, time) in recent.all) {
            if (!allowRepeats || now - (time as Long) > REPEAT_WINDOW_MS) edit.remove(key)
        }
        edit.apply()
    }

    // True if this number was blocked within the window, so this call should ring. Otherwise
    // remembers it for next time. Only asked about calls that would be blocked. Hidden numbers
    // can't be told apart, so they never count as repeats.
    private fun isRepeatCall(number: String?, now: Long): Boolean {
        if (number.isNullOrBlank()) return false
        val key = PhoneNumberUtils.normalizeNumber(number)
        if (recent.contains(key)) return true // expired entries were just removed
        // Keep the first call's time, so further calls don't extend the window.
        recent.edit().putLong(key, now).apply()
        return false
    }

    private fun isContact(number: String): Boolean = try {
        val uri = Uri.withAppendedPath(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    } catch (e: SecurityException) {
        true // no contacts permission: fail open rather than block everything
    }
}
