package com.openbible

import android.app.Application
import com.openbible.data.ReadingPlanSeeder
import com.openbible.data.db.OpenBibleDatabase
import com.openbible.data.locations.EventImporter
import com.openbible.data.locations.LocationImporter
import com.openbible.data.locations.ParallelTraditionImporter
import com.openbible.data.preferences.UserPreferences
import com.openbible.data.strongs.StrongImporter
import com.openbible.data.translation.TranslationImporter
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

    @Inject
    lateinit var translationImporter: TranslationImporter

    @Inject
    lateinit var eventImporter: EventImporter

    @Inject
    lateinit var parallelTraditionImporter: ParallelTraditionImporter

    override fun onCreate() {
        super.onCreate()
        DailyVerseReceiver.createNotificationChannel(this)

        // ponytail: fire-and-forget imports on first launch.
        // Blocks IO thread briefly once; skips via SharedPreferences on subsequent launches.
        CoroutineScope(Dispatchers.IO).launch {
            strongImporter.importIfNeeded()
            locationImporter.importIfNeeded()
            eventImporter.importIfNeeded()
            parallelTraditionImporter.importIfNeeded()
            translationImporter.importMissing(database.openHelper.writableDatabase)
            ReadingPlanSeeder.ensureSeeded(database.readingPlanDao(), database.bibleDao())
        }
    }
}
