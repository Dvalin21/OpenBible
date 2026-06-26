package com.openbible

import android.app.Application
import com.openbible.data.db.OpenBibleDatabase
import com.openbible.data.preferences.UserPreferences
import com.openbible.data.locations.LocationImporter
import com.openbible.data.strongs.StrongImporter
import com.openbible.notification.DailyVerseReceiver
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var strongImporter: StrongImporter

    @Inject
    lateinit var locationImporter: LocationImporter

    override fun onCreate() {
        super.onCreate()
        DailyVerseReceiver.createNotificationChannel(this)

        // ponytail: fire-and-forget imports on first launch.
        // Blocks IO thread briefly once; skips via SharedPreferences on subsequent launches.
        CoroutineScope(Dispatchers.IO).launch {
            strongImporter.importIfNeeded()
            locationImporter.importIfNeeded()
        }
    }
}
