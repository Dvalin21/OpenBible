package com.openbible.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.OpenBibleApp
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.SearchResult
import com.openbible.data.db.entity.TranslationEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Search screen state.
 *
 * Debounces query input by 300ms before hitting the DB,
 * groups results by book, tracks loading/error/idle states.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenBibleApp
    private val bibleDao: BibleDao = app.database.bibleDao()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results.asStateFlow()

    private val _selectedTranslation = MutableStateFlow("kjv")
    val selectedTranslation: StateFlow<String> = _selectedTranslation.asStateFlow()

    val translations: StateFlow<List<TranslationEntity>> = bibleDao
        .getBundledTranslations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var searchJob: Job? = null

    /** Update the search query (debounced 300ms). */
    fun setQuery(q: String) {
        _query.value = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            performSearch(q.trim())
        }
    }

    /** Change the active translation. */
    fun selectTranslation(translationId: String) {
        _selectedTranslation.value = translationId
        val q = _query.value.trim()
        if (q.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(q)
            }
        }
    }

    /** Search across all translations. */
    fun searchAll() {
        _selectedTranslation.value = ""
        val q = _query.value.trim()
        if (q.isNotEmpty()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(q)
            }
        }
    }

    private suspend fun performSearch(q: String) {
        if (q.length < 2) {
            _results.value = emptyList()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        _hasSearched.value = true

        val translationId = _selectedTranslation.value
        _results.value = if (translationId.isNotBlank()) {
            bibleDao.searchVersesWithBook(translationId, q)
        } else {
            bibleDao.searchAllTranslations(q)
        }

        _isSearching.value = false
    }
}
