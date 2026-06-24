package com.openbible

import android.app.Application
import com.openbible.data.db.OpenBibleDatabase
import com.openbible.data.preferences.UserPreferences
import com.openbible.notification.DailyVerseReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * OpenBible Application class.
 *
 * Hilt entry point. Database and preferences are injected by Hilt
 * and accessible from any ViewModel or component that needs them.
 */
@HiltAndroidApp
class OpenBibleApp : Application() {

    @Inject
    lateinit var database: OpenBibleDatabase

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate() {
        super.onCreate()
        DailyVerseReceiver.createNotificationChannel(this)
    }
}
