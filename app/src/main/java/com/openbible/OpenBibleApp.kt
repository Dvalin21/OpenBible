package com.openbible

import android.app.Application
import android.content.pm.ApplicationInfo
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
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

    /**
     * False until the database file is copied from assets and migrated.
     * MainActivity gates first render on this — Room opens the DB on the
     * calling thread, and a 200MB copy + FTS rebuild on main is an ANR.
     */
    private val _databaseReady = MutableStateFlow(false)
    val databaseReady: StateFlow<Boolean> = _databaseReady.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        }
        DailyVerseReceiver.createNotificationChannel(this)

        // ponytail: fire-and-forget imports on first launch, sequential in one
        // IO coroutine so each step sees the previous one's rows.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            // Open the DB here (IO) so copy + migrations never run on main.
            runCatching { database.openHelper.writableDatabase }
            _databaseReady.value = true
            try { strongImporter.importIfNeeded() } catch (e: Exception) { Timber.e(e, "StrongImporter failed") }
            try { locationImporter.importIfNeeded() } catch (e: Exception) { Timber.e(e, "LocationImporter failed") }
            try { eventImporter.importIfNeeded() } catch (e: Exception) { Timber.e(e, "EventImporter failed") }
            try { parallelTraditionImporter.importIfNeeded() } catch (e: Exception) { Timber.e(e, "ParallelTraditionImporter failed") }
            try { translationImporter.importMissing() } catch (e: Exception) { Timber.e(e, "TranslationImporter failed") }
            try { ReadingPlanSeeder.ensureSeeded(database.readingPlanDao(), database.bibleDao()) } catch (e: Exception) { Timber.e(e, "ReadingPlanSeeder failed") }
        }
    }
}
