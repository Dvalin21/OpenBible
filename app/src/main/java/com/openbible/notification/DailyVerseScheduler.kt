package com.openbible.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Schedules the daily verse alarm using AlarmManager.setWindow().
 *
 * No special permissions needed for setWindow() on API 29+ (our minSdk).
 * The alarm fires within a ~5 minute window of the target time.
 */
object DailyVerseScheduler {

    private const val REQUEST_CODE_SCHEDULE = 1001

    /**
     * Schedule or reschedule the daily verse alarm.
     * If the target time has passed for today, schedules for tomorrow.
     */
    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If the target time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val intent = Intent(context, DailyVerseReceiver::class.java).apply {
            action = DailyVerseReceiver.ACTION_SHOW_DAILY_VERSE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SCHEDULE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // setWindow allows the system ~5 minutes of flex for battery optimization
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES / 3,  // ~5 minute window
            pendingIntent
        )
    }

    /** Cancel the daily verse alarm. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyVerseReceiver::class.java).apply {
            action = DailyVerseReceiver.ACTION_SHOW_DAILY_VERSE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SCHEDULE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}
