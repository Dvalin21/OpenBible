package com.openbible.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.OpenBibleApp
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.entity.TranslationEntity
import com.openbible.data.model.ThemeMode
import com.openbible.data.preferences.UserPreferences
import com.openbible.notification.DailyVerseScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Settings screen state — wraps UserPreferences flows as StateFlows.
 *
 * The data is stored in DataStore and exposed via StateFlow for Compose.
 * Every setter is a suspend function that writes directly to DataStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenBibleApp
    private val prefs: UserPreferences = app.userPreferences
    private val bibleDao: BibleDao = app.database.bibleDao()

    // ── Read from DataStore ──────────────────────────────────────

    val themeMode: StateFlow<ThemeMode> = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.LIGHT)

    val fontSizeVerseNumbers: StateFlow<Float> = prefs.fontSizeVerseNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 18f)

    val fontSizeVerseText: StateFlow<Float> = prefs.fontSizeVerseText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16f)

    val lineSpacing: StateFlow<Float> = prefs.lineSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.6f)

    val pageFlipSound: StateFlow<Boolean> = prefs.pageFlipSound
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pageFlipAnimation: StateFlow<Boolean> = prefs.pageFlipAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val defaultTranslation: StateFlow<String> = prefs.defaultTranslation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "kjv")

    val dailyVerseEnabled: StateFlow<Boolean> = prefs.dailyVerseEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val dailyVerseTime: StateFlow<Pair<Int, Int>> = prefs.dailyVerseTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(7, 0))

    /** Available translations for the default translation selector. */
    val translations: StateFlow<List<TranslationEntity>> = bibleDao
        .getBundledTranslations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Writes to DataStore ──────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setFontSizeVerseNumbers(size: Float) {
        viewModelScope.launch { prefs.setFontSizeVerseNumbers(size) }
    }

    fun setFontSizeVerseText(size: Float) {
        viewModelScope.launch { prefs.setFontSizeVerses(size) }
    }

    fun setLineSpacing(spacing: Float) {
        viewModelScope.launch { prefs.setLineSpacing(spacing) }
    }

    fun setPageFlipSound(enabled: Boolean) {
        viewModelScope.launch { prefs.setPageFlipSound(enabled) }
    }

    fun setPageFlipAnimation(enabled: Boolean) {
        viewModelScope.launch { prefs.setPageFlipAnimation(enabled) }
    }

    fun setDefaultTranslation(translationId: String) {
        viewModelScope.launch { prefs.setDefaultTranslation(translationId) }
    }

    fun setDailyVerseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setDailyVerseEnabled(enabled)
            val context = getApplication<OpenBibleApp>()
            if (enabled) {
                val (hour, minute) = prefs.dailyVerseTime.first()
                DailyVerseScheduler.schedule(context, hour, minute)
            } else {
                DailyVerseScheduler.cancel(context)
            }
        }
    }

    fun setDailyVerseTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            prefs.setDailyVerseTime(hour, minute)
            // Reschedule if currently enabled
            val enabled = prefs.dailyVerseEnabled.first()
            if (enabled) {
                val context = getApplication<OpenBibleApp>()
                DailyVerseScheduler.schedule(context, hour, minute)
            }
        }
    }
}
