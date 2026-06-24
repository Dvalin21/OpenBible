package com.openbible.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.data.db.dao.ReadingHistoryDao
import com.openbible.data.db.dao.ReadingHistoryWithVerse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Home screen state — last reading position, stats, daily verse.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val readingHistoryDao: ReadingHistoryDao
) : ViewModel() {

    /** Most recent reading position, null if no reading history. */
    val lastRead: StateFlow<ReadingHistoryWithVerse?> = readingHistoryDao
        .getMostRecentReading()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
