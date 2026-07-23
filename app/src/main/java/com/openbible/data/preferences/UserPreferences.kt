package com.openbible.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openbible.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level extension property — DataStore is a singleton per process
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openbible_prefs")

/**
 * User preferences backed by DataStore.
 *
 * All preferences have sensible defaults — the app works without
 * any preferences being set. Default translation falls back to "kjv"
 * if no preference is stored.
 */
class UserPreferences(private val context: Context) {

    // -- Keys --

    private companion object {
        val KEY_DEFAULT_TRANSLATION = stringPreferencesKey("default_translation")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_FONT_SIZE = floatPreferencesKey("font_size")
        val KEY_FONT_SIZE_VERSES = floatPreferencesKey("font_size_verses")
        val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        val KEY_PAGE_FLIP_SOUND = booleanPreferencesKey("page_flip_sound")
        val KEY_PAGE_FLIP_ANIMATION = booleanPreferencesKey("page_flip_animation")
        val KEY_DAILY_VERSE_ENABLED = booleanPreferencesKey("daily_verse_enabled")
        val KEY_DAILY_VERSE_TIME_HOUR = intPreferencesKey("daily_verse_time_hour")
        val KEY_DAILY_VERSE_TIME_MINUTE = intPreferencesKey("daily_verse_time_minute")
        val KEY_LAST_READ_TRANSLATION = stringPreferencesKey("last_read_translation")
        val KEY_LAST_READ_BOOK = intPreferencesKey("last_read_book")
        val KEY_LAST_READ_CHAPTER = intPreferencesKey("last_read_chapter")
    }

    // -- Defaults --

    private val DEFAULT_FONT_SIZE = 18f
    private val DEFAULT_FONT_SIZE_VERSES = 16f
    private val DEFAULT_LINE_SPACING = 1.6f

    // -- Translation --

    val defaultTranslation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_TRANSLATION] ?: "kjv"
    }

    suspend fun setDefaultTranslation(translationId: String) {
        context.dataStore.edit { it[KEY_DEFAULT_TRANSLATION] = translationId }
    }

    // -- Theme --

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        try {
            prefs[KEY_THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.LIGHT
        } catch (_: Exception) {
            ThemeMode.LIGHT
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    // -- Font Size --

    val fontSizeVerseNumbers: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SIZE] ?: DEFAULT_FONT_SIZE
    }

    val fontSizeVerseText: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_FONT_SIZE_VERSES] ?: DEFAULT_FONT_SIZE_VERSES
    }

    suspend fun setFontSizeVerses(size: Float) {
        context.dataStore.edit { it[KEY_FONT_SIZE_VERSES] = size }
    }

    suspend fun setFontSizeVerseNumbers(size: Float) {
        context.dataStore.edit { it[KEY_FONT_SIZE] = size }
    }

    // -- Line Spacing --

    val lineSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_LINE_SPACING] ?: DEFAULT_LINE_SPACING
    }

    suspend fun setLineSpacing(spacing: Float) {
        context.dataStore.edit { it[KEY_LINE_SPACING] = spacing }
    }

    // -- Page Flip --

    val pageFlipSound: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_FLIP_SOUND] ?: true
    }

    val pageFlipAnimation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PAGE_FLIP_ANIMATION] ?: true
    }

    suspend fun setPageFlipSound(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PAGE_FLIP_SOUND] = enabled }
    }

    suspend fun setPageFlipAnimation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PAGE_FLIP_ANIMATION] = enabled }
    }

    // -- Daily Verse --

    val dailyVerseEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DAILY_VERSE_ENABLED] ?: true
    }

    val dailyVerseTime: Flow<Pair<Int, Int>> = context.dataStore.data.map { prefs ->
        Pair(
            prefs[KEY_DAILY_VERSE_TIME_HOUR] ?: 7,
            prefs[KEY_DAILY_VERSE_TIME_MINUTE] ?: 0
        )
    }

    suspend fun setDailyVerseEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DAILY_VERSE_ENABLED] = enabled }
    }

    suspend fun setDailyVerseTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[KEY_DAILY_VERSE_TIME_HOUR] = hour
            it[KEY_DAILY_VERSE_TIME_MINUTE] = minute
        }
    }

    // -- Reading Resume --

    val lastReadingPosition: Flow<Triple<String?, Int, Int>> = context.dataStore.data.map { prefs ->
        Triple(
            prefs[KEY_LAST_READ_TRANSLATION],
            prefs[KEY_LAST_READ_BOOK] ?: 1,
            prefs[KEY_LAST_READ_CHAPTER] ?: 1
        )
    }

    suspend fun setLastReadingPosition(translationId: String, bookId: Int, chapter: Int) {
        context.dataStore.edit {
            it[KEY_LAST_READ_TRANSLATION] = translationId
            it[KEY_LAST_READ_BOOK] = bookId
            it[KEY_LAST_READ_CHAPTER] = chapter
        }
    }
}
