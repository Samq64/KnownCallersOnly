package dev.samuelq.callscreen

import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telecom.Call
import android.telecom.CallScreeningService

class BlockService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val enabled = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
        val number = details.handle?.schemeSpecificPart
        val block = enabled &&
            details.callDirection == Call.Details.DIRECTION_INCOMING &&
            (number.isNullOrBlank() || !isContact(number))
        respondToCall(
            details,
            CallResponse.Builder()
                .setDisallowCall(block)
                .setRejectCall(block)
                .setSkipNotification(block)
                .build()
        )
    }

    private fun isContact(number: String): Boolean = try {
        val uri = Uri.withAppendedPath(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(number))
        contentResolver.query(uri, arrayOf(PhoneLookup._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    } catch (e: SecurityException) {
        true // no contacts permission: fail open rather than block everything
    }
}
