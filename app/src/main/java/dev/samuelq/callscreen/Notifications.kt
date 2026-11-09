package dev.samuelq.callscreen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

private const val CHANNEL = "blocked"
private const val GROUP = "blocked"
private const val SUMMARY_ID = 0
private const val CALL_ID = 1 // with the normalized number as the tag
private const val ACTION_CALL_BACK = "dev.samuelq.callscreen.CALL_BACK"
private const val ACTION_ADD_CONTACT = "dev.samuelq.callscreen.ADD_CONTACT"

fun Context.canNotify(): Boolean {
    val nm = getSystemService(NotificationManager::class.java)
    return nm.areNotificationsEnabled() &&
        nm.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
}

fun Context.formatNumber(number: String): String {
    val country = getSystemService(TelephonyManager::class.java)?.simCountryIso
        ?.takeIf { it.isNotEmpty() } ?: Locale.getDefault().country
    return PhoneNumberUtils.formatNumber(number, country.uppercase()) ?: number
}

fun Context.notifyBlocked(number: String, time: Long) {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL, getString(R.string.channel_blocked), NotificationManager.IMPORTANCE_LOW)
    )

    val openApp = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_tile)
        .setContentTitle(getString(R.string.notify_blocked))
        .setContentText(formatNumber(number))
        .setWhen(time)
        .setShowWhen(true)
        .setGroup(GROUP)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .addAction(action(ACTION_CALL_BACK, R.string.call_back, number))
        .addAction(action(ACTION_ADD_CONTACT, R.string.add_contact, number))
        .build()
    nm.notify(PhoneNumberUtils.normalizeNumber(number), CALL_ID, notification)

    // Explicit groups need a summary to collapse together.
    nm.notify(SUMMARY_ID, Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_tile)
        .setGroup(GROUP)
        .setGroupSummary(true)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build())
}

// Removes the number's notification, e.g. once it's acted on or its call was let through.
fun Context.dismissBlocked(number: String) {
    val nm = getSystemService(NotificationManager::class.java)
    val tag = PhoneNumberUtils.normalizeNumber(number)
    nm.cancel(tag, CALL_ID)
    // Cancelling may not be reflected yet, so skip this number when checking for others.
    if (nm.activeNotifications.none { it.id == CALL_ID && it.tag != tag }) nm.cancel(SUMMARY_ID)
}

// The number in the data URI keeps each notification's PendingIntents distinct.
private fun Context.action(action: String, label: Int, number: String) = Notification.Action.Builder(
    null, getString(label),
    PendingIntent.getActivity(
        this, 0,
        Intent(this, NotificationAction::class.java).setAction(action).setData(Uri.fromParts("tel", number, null)),
        PendingIntent.FLAG_IMMUTABLE,
    ),
).build()

// Invisible step between a notification button and the app it opens, so the notification can be
// dismissed first (action buttons don't auto-cancel, and receivers can't start activities).
class NotificationAction : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tel = intent.data
        val number = tel?.schemeSpecificPart
        val target = when (intent.action) {
            ACTION_CALL_BACK -> Intent(Intent.ACTION_DIAL, tel)
            ACTION_ADD_CONTACT -> Intent(ContactsContract.Intents.Insert.ACTION)
                .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                .putExtra(ContactsContract.Intents.Insert.PHONE, number)
            else -> null
        }
        if (target != null && !number.isNullOrBlank()) {
            try {
                startActivity(target)
                dismissBlocked(number)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_app, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
