package com.openbible.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import com.openbible.MainActivity
import com.openbible.OpenBibleApp
import com.openbible.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Receives the daily verse alarm and shows a notification.
 *
 * Also handles BOOT_COMPLETED to reschedule the alarm after reboot.
 * Uses goAsync() + coroutine for proper BroadcastReceiver lifecycle.
 * The BroadcastReceiver has ~10 seconds to complete.
 */
class DailyVerseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_SHOW_DAILY_VERSE -> showDailyVerse(context)
                    Intent.ACTION_BOOT_COMPLETED -> rescheduleAfterBoot(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun showDailyVerse(context: Context) {
        val app = context.applicationContext as OpenBibleApp
        val prefs = app.userPreferences

        // Check if daily verse is still enabled
        val enabled = prefs.dailyVerseEnabled.first()
        if (!enabled) return

        // Default to KJV if we can't read the preference
        val translationId = prefs.defaultTranslation.first()

        // Query a random verse from the database
        val verse = app.database.withTransaction {
            app.database.bibleDao().getRandomVerse(translationId)
        }

        if (verse == null) {
            // Schedule next day anyway (maybe next time)
            scheduleNext(context, prefs)
            return
        }

        val bookName = app.database.bibleDao().getBook(verse.bookId)?.name ?: "Bible"

        val reference = "$bookName ${verse.chapter}:${verse.verse}"

        // Store verse data for the home screen widget
        DailyVerseWidgetProvider.storeVerse(context, verse.text, reference)
        DailyVerseWidgetProvider.refreshAll(context)

        // Build the notification
        createNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("translationId", translationId)
            putExtra("bookId", verse.bookId)
            putExtra("chapter", verse.chapter)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Verse — $reference")
            .setContentText(verse.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(verse.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_DAILY_VERSE, notification)

        // Schedule next day's alarm
        scheduleNext(context, prefs)
    }

    private fun scheduleNext(context: Context, prefs: com.openbible.data.preferences.UserPreferences) {
        val (hour, minute) = kotlinx.coroutines.runBlocking {
            prefs.dailyVerseTime.firstOrNull() ?: Pair(7, 0)
        }
        DailyVerseScheduler.schedule(context, hour, minute)
    }

    private fun rescheduleAfterBoot(context: Context) {
        val prefs = (context.applicationContext as OpenBibleApp).userPreferences
        val enabled = kotlinx.coroutines.runBlocking {
            prefs.dailyVerseEnabled.firstOrNull() ?: true
        }
        if (!enabled) return

        val (hour, minute) = kotlinx.coroutines.runBlocking {
            prefs.dailyVerseTime.firstOrNull() ?: Pair(7, 0)
        }
        DailyVerseScheduler.schedule(context, hour, minute)
    }

    companion object {
        const val ACTION_SHOW_DAILY_VERSE = "com.openbible.action.SHOW_DAILY_VERSE"
        const val CHANNEL_ID = "daily_verse"
        private const val NOTIFICATION_ID_DAILY_VERSE = 2001

        /** Create the notification channel (safe to call multiple times). */
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Verse",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A daily Bible verse notification"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
