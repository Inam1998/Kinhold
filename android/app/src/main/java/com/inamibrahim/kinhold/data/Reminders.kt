package com.inamibrahim.kinhold.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.inamibrahim.kinhold.MainActivity
import com.inamibrahim.kinhold.model.Upcoming
import com.inamibrahim.kinhold.model.UpcomingKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The optional morning reminder for birthdays, remembrance days and anniversaries. It is a once-a-day alarm that wakes
 * a small receiver, which reads the tree on the phone and posts one notification if today or tomorrow has something. It
 * uses no internet and no exact-alarm permission, so it can be a little late. It is off until the person turns it on.
 */
object Reminders {
    private const val PREFS = "kinhold"
    private const val KEY = "reminders_on"
    const val ACTION = "com.inamibrahim.kinhold.REMIND"
    private const val CHANNEL = "dates"

    fun enabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        if (on) schedule(context) else cancel(context)
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 7, Intent(context, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The next 9 o'clock in the morning, today if it has not passed. */
    private fun nextNine(now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val today = LocalDateTime.of(now.toLocalDate(), LocalTime.of(9, 0))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun schedule(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = nextNine().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setInexactRepeating(AlarmManager.RTC, at, AlarmManager.INTERVAL_DAY, pending(context))
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending(context))
    }

    /** Reads the tree and posts the notification for today and tomorrow, if there is anything to say and notifications are allowed. */
    suspend fun notifyNow(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        val repo = FamilyRepository(context)
        repo.load()
        val today = LocalDate.now()
        val items = Upcoming.compute(repo.family.value, repo.marriageDates(), today, withinDays = 1)
        if (items.isEmpty()) return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Birthdays and anniversaries", NotificationManager.IMPORTANCE_DEFAULT))
        fun line(i: com.inamibrahim.kinhold.model.UpcomingDate): String {
            val what = when (i.kind) {
                UpcomingKind.Birthday -> "${i.person.name}'s birthday" + (i.years?.let { " ($it)" } ?: "")
                UpcomingKind.Remembrance -> "Remembering ${i.person.name}" + (i.years?.let { ", $it years" } ?: "")
                UpcomingKind.Anniversary -> "${i.person.name} and ${i.other?.name ?: "spouse"}: anniversary" + (i.years?.let { " ($it)" } ?: "")
            }
            return "${Upcoming.describe(i.daysAway)}: $what"
        }
        val lines = items.take(6).map(::line)
        val open = PendingIntent.getActivity(context, 8, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // The lock screen shows only a general line, so names are not read by someone who just glances at the phone.
        val general = Notification.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle("Family dates").setContentText("Open Kinhold to see today's dates").build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (items.size == 1) lines[0] else "${items.size} family dates")
            .setContentText(lines.first())
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(general)
            .build()
        manager.notify(1, notification)
    }
}

/** Wakes once a day for the reminder, and sets the daily alarm again after the phone restarts. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> if (Reminders.enabled(context)) Reminders.schedule(context)
            Reminders.ACTION -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        if (Reminders.enabled(context)) Reminders.notifyNow(context)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
}
