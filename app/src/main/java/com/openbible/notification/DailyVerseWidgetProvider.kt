package com.openbible.notification

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.openbible.MainActivity
import com.openbible.R

/**
 * Home screen widget showing the daily verse.
 *
 * Verse data is stored in SharedPreferences by [DailyVerseReceiver]
 * when the daily notification fires. The widget reads from there,
 * avoiding direct database access in the widget process.
 *
 * updatePeriodMillis is 24h, but the widget is also refreshed
 * whenever a new daily verse notification fires.
 */
class DailyVerseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val PREFS_NAME = "widget_verse"
        private const val KEY_TEXT = "verse_text"
        private const val KEY_REF = "verse_ref"

        /** Build and push the RemoteViews for one widget instance. */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val verseText = prefs.getString(KEY_TEXT, null)
                ?: "In the beginning God created the heavens and the earth."
            val verseRef = prefs.getString(KEY_REF, null) ?: "Genesis 1:1"

            val views = RemoteViews(context.packageName, R.layout.widget_daily_verse)
            views.setTextViewText(R.id.widget_verse_text, verseText)
            views.setTextViewText(R.id.widget_verse_ref, verseRef)

            // Tap opens the Bible app
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /** Store verse data for the widget to read. */
        fun storeVerse(context: Context, text: String, reference: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEXT, text)
                .putString(KEY_REF, reference)
                .apply()
        }

        /** Refresh all widget instances. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, DailyVerseWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }
}
