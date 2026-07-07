package com.openbible.ui.readingplan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.OpenBibleApp
import com.openbible.data.ReadingPlanSeeder
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.ReadingPlanDao
import com.openbible.data.db.entity.ReadingPlanDayEntity
import com.openbible.data.db.entity.ReadingPlanEntity
import com.openbible.data.db.entity.ReadingProgressEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

/**
 * ViewModel for the reading plans screen.
 *
 * Manages plan list, active plan state, current day reading,
 * and progress tracking. Seeds the default "Bible in a Year" plan
 * on first access.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReadingPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenBibleApp
    private val planDao: ReadingPlanDao = app.database.readingPlanDao()
    private val bibleDao: BibleDao = app.database.bibleDao()

    /** Cache of bookId → book name, populated once from DB. */
    private val bookNames = ConcurrentHashMap<Int, String>()

    init {
        // ponytail: load book names via coroutine, ConcurrentHashMap handles concurrent reads
        viewModelScope.launch {
            bibleDao.getBooks("kjv").first().forEach { book ->
                bookNames[book.id] = book.name
            }
        }
    }

    // ── Plan List ───────────────────────────────────────────────

    val plans: StateFlow<List<ReadingPlanEntity>> = planDao.getAllPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Active Plan State ───────────────────────────────────────

    private val _activePlanId = MutableStateFlow<Long?>(null)
    val activePlanId: StateFlow<Long?> = _activePlanId.asStateFlow()

    val activePlan: StateFlow<ReadingPlanEntity?> = _activePlanId
        .flatMapLatest { id ->
            if (id == null) flowOf<ReadingPlanEntity?>(null)
            else flow { emit(planDao.getPlan(id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Current day number being viewed (1-indexed). */
    private val _currentDay = MutableStateFlow(1)
    val currentDay: StateFlow<Int> = _currentDay.asStateFlow()

    /** Today's reading plan day. */
    val currentPlanDay: StateFlow<ReadingPlanDayEntity?> = combine(
        _activePlanId, _currentDay
    ) { planId, day -> Pair(planId, day) }
        .flatMapLatest { (planId, day) ->
            if (planId == null) flowOf<ReadingPlanDayEntity?>(null)
            else flow { emit(planDao.getPlanDay(planId, day)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Progress for the currently viewed day. */
    val currentDayProgress: StateFlow<ReadingProgressEntity?> = combine(
        _activePlanId, _currentDay
    ) { planId, day -> Pair(planId, day) }
        .flatMapLatest { (planId, day) ->
            if (planId == null) flowOf<ReadingProgressEntity?>(null)
            else flow { emit(planDao.getDayProgress(planId, day)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Total chapters in the plan (for progress %) */
    val planProgress: StateFlow<Float> = _activePlanId
        .flatMapLatest { id ->
            if (id == null) flowOf(0f)
            else planDao.getProgress(id).map { progress ->
                val totalDays = activePlan.value?.durationDays ?: 365
                if (totalDays == 0) 0f
                else progress.count { it.completed }.toFloat() / totalDays.toFloat()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    // ── Actions ─────────────────────────────────────────────────

    /** Seed plans on first access and auto-start the first plan. */
    fun initialize() {
        viewModelScope.launch {
            ReadingPlanSeeder.ensureSeeded(planDao, bibleDao)

            // Auto-select first plan if none active
            val plans = planDao.getAllPlansOnce()
            if (_activePlanId.value == null && plans.isNotEmpty()) {
                _activePlanId.value = plans.first().id

                // Find current day: last completed + 1, or day 1
                val progress = planDao.getProgressOnce(plans.first().id)
                val lastDone = progress.maxOfOrNull { it.dayNumber } ?: 0
                _currentDay.value = if (lastDone >= plans.first().durationDays)
                    plans.first().durationDays  // finished
                else
                    lastDone + 1
            }
        }
    }

    /** Parse readings JSON into a list of reading items with resolved book names. */
    fun parseReadings(readingsJson: String): List<ReadingItem> {
        return try {
            val arr = JSONArray(readingsJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val bookId = obj.getInt("bookId")
                ReadingItem(
                    bookId = bookId,
                    chapter = obj.getInt("chapter"),
                    bookName = bookNames[bookId] ?: "Book $bookId"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Mark the current day as complete. */
    fun markDayComplete() {
        viewModelScope.launch {
            val planId = _activePlanId.value ?: return@launch
            val day = _currentDay.value
            val existing = planDao.getDayProgress(planId, day)
            if (existing != null) {
                planDao.updateProgress(planId, day, true, System.currentTimeMillis())
            } else {
                planDao.upsertProgress(
                    ReadingProgressEntity(
                        planId = planId,
                        dayNumber = day,
                        completed = true,
                        completedAt = System.currentTimeMillis()
                    )
                )
            }
            // Auto-advance to next day
            val plan = planDao.getPlan(planId) ?: return@launch
            if (day < plan.durationDays) {
                _currentDay.value = day + 1
            }
        }
    }

    /** Navigate to a specific day. */
    fun goToDay(day: Int) {
        _currentDay.value = day.coerceAtLeast(1)
    }

    /** Select a plan. */
    fun selectPlan(planId: Long) {
        _activePlanId.value = planId
        _currentDay.value = 1
    }
}

/** A single reading item: read [chapter] of [bookId]. */
data class ReadingItem(
    val bookId: Int,
    val chapter: Int,
    val bookName: String = "Book $bookId"
)
