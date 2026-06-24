package com.openbible.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.OpenBibleApp
import com.openbible.data.db.dao.ReadingHistoryWithVerse
import com.openbible.data.db.dao.ReadingHistoryDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Home screen state — last reading position, stats, daily verse.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenBibleApp
    private val readingHistoryDao: ReadingHistoryDao = app.database.readingHistoryDao()

    /** Most recent reading position, null if no reading history. */
    val lastRead: StateFlow<ReadingHistoryWithVerse?> = readingHistoryDao
        .getMostRecentReading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
