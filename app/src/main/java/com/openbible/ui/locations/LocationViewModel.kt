package com.openbible.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.data.db.dao.LocationDao
import com.openbible.data.db.dao.LocationVerseLink
import com.openbible.data.db.entity.BibleLocationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationDao: LocationDao
) : ViewModel() {

    /** All locations for the browse list. */
    val allLocations: StateFlow<List<BibleLocationEntity>> = locationDao
        .getAllLocationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Search query state. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Search results. Reactive to query changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<BibleLocationEntity>> = _query
        .flatMapLatest { q ->
            if (q.isBlank()) locationDao.getAllLocationsFlow()
            else kotlinx.coroutines.flow.flowOf(locationDao.searchLocations(q))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(q: String) { _query.value = q }

    /** Selected location detail. */
    private val _selectedLocation = MutableStateFlow<BibleLocationEntity?>(null)
    val selectedLocation: StateFlow<BibleLocationEntity?> = _selectedLocation.asStateFlow()

    /** Verse references for the selected location. */
    private val _verseLinks = MutableStateFlow<List<LocationVerseLink>>(emptyList())
    val verseLinks: StateFlow<List<LocationVerseLink>> = _verseLinks.asStateFlow()

    fun selectLocation(id: String) {
        viewModelScope.launch {
            _selectedLocation.value = locationDao.getLocation(id)
            _verseLinks.value = locationDao.getLocationVerseLinks(id)
        }
    }
}
