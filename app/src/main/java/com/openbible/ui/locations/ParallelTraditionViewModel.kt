package com.openbible.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.data.db.dao.ParallelTraditionDao
import com.openbible.data.db.entity.ParallelTraditionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ParallelTraditionViewModel @Inject constructor(
    private val parallelTraditionDao: ParallelTraditionDao
) : ViewModel() {

    private val _parallels = MutableStateFlow<List<ParallelTraditionEntity>>(emptyList())
    val parallels: StateFlow<List<ParallelTraditionEntity>> = _parallels.asStateFlow()

    private val _title = MutableStateFlow("Parallel Traditions")
    val title: StateFlow<String> = _title.asStateFlow()

    private var currentEventId: String? = null

    fun setEventId(eventId: String?) {
        if (eventId == currentEventId) return
        currentEventId = eventId
        viewModelScope.launch {
            val result = if (eventId != null) {
                _title.value = "Parallel Traditions"
                parallelTraditionDao.getParallelsForEvent(eventId)
            } else {
                _title.value = "All Parallel Traditions"
                parallelTraditionDao.getAllParallels()
            }
            _parallels.value = result
        }
    }
}
