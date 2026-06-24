package com.openbible.ui.bible

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.OpenBibleApp
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.CrossReferenceDao
import com.openbible.data.db.dao.CrossReferenceDisplay
import com.openbible.data.db.entity.*
import com.openbible.data.model.HighlightColor
import com.openbible.data.preferences.UserPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Bible reading screen.
 *
 * Manages three pieces of state: available translations, the current
 * book/chapter/verse list, and the user's preferences for navigation.
 * Also manages bookmarks and highlights for the current chapter.
 *
 * No network calls — all data is local from Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BibleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OpenBibleApp
    private val bibleDao: BibleDao = app.database.bibleDao()
    private val readingHistoryDao = app.database.readingHistoryDao()
    private val bookmarkDao = app.database.bookmarkDao()
    private val highlightDao = app.database.highlightDao()
    private val crossReferenceDao: CrossReferenceDao = app.database.crossReferenceDao()
    private val preferences: UserPreferences = app.userPreferences

    // -- Translations --

    /** All bundled translations. */
    val translations: StateFlow<List<TranslationEntity>> = bibleDao
        .getBundledTranslations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -- Current Selection --

    private val _selectedTranslationId = MutableStateFlow("kjv")
    val selectedTranslationId: StateFlow<String> = _selectedTranslationId.asStateFlow()

    private val _selectedBookId = MutableStateFlow(1)
    val selectedBookId: StateFlow<Int> = _selectedBookId.asStateFlow()

    private val _selectedChapter = MutableStateFlow(1)
    val selectedChapter: StateFlow<Int> = _selectedChapter.asStateFlow()

    // -- Secondary Translation (split-pane compare mode) --

    private val _selectedSecondaryTranslationId = MutableStateFlow<String?>(null)
    val selectedSecondaryTranslationId: StateFlow<String?> =
        _selectedSecondaryTranslationId.asStateFlow()

    /** Verses for secondary translation in split-pane mode (same book/chapter). */
    val secondaryVerses: StateFlow<List<VerseEntity>> = _selectedSecondaryTranslationId
        .flatMapLatest { secId ->
            if (secId == null) flowOf(emptyList())
            else combine(_selectedBookId, _selectedChapter) { bookId, chapter ->
                Pair(bookId, chapter)
            }.flatMapLatest { (bookId, chapter) ->
                bibleDao.getVerses(secId, bookId, chapter)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Secondary translation metadata (for display labels). */
    val secondaryTranslation: StateFlow<TranslationEntity?> = _selectedSecondaryTranslationId
        .flatMapLatest { id ->
            flow { emit(if (id == null) null else bibleDao.getTranslation(id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // -- Books for current translation --

    val books: StateFlow<List<BookEntity>> = _selectedTranslationId
        .flatMapLatest { translationId -> bibleDao.getBooks(translationId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -- Chapters for current book --

    val chapters: StateFlow<List<Int>> = combine(
        _selectedTranslationId,
        _selectedBookId
    ) { translationId, bookId -> Pair(translationId, bookId) }
        .flatMapLatest { (translationId, bookId) ->
            bibleDao.getChapters(translationId, bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -- Verses for current chapter --

    val verses: StateFlow<List<VerseEntity>> = combine(
        _selectedTranslationId,
        _selectedBookId,
        _selectedChapter
    ) { translationId, bookId, chapter ->
        Triple(translationId, bookId, chapter)
    }
        .flatMapLatest { (translationId, bookId, chapter) ->
            bibleDao.getVerses(translationId, bookId, chapter)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -- Current book metadata --

    val currentBook: StateFlow<BookEntity?> = _selectedBookId
        .flatMapLatest { bookId ->
            flow { emit(bibleDao.getBook(bookId)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Bookmarks for current chapter ───────────────────────────

    /** Set of verse IDs that are bookmarked in the current chapter. */
    val bookmarkedVerseIds: StateFlow<Set<Long>> = verses
        .flatMapLatest { verseList ->
            if (verseList.isEmpty()) flowOf(emptySet())
            else bookmarkDao.observeBookmarksForVerses(verseList.map { it.id })
                .map { bookmarks -> bookmarks.map { it.verseId }.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // ── Highlights for current chapter ───────────────────────────

    // ── Cross-references for current chapter ──────────────────────

    /** Map of current verse ID → cross-references for that verse.
     *  Cross-refs are anchored to KJV; we map via (bookId, chapter, verse). */
    val crossReferenceMap: StateFlow<Map<Long, List<CrossReferenceDisplay>>> = verses
        .flatMapLatest { verseList ->
            if (verseList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                flow {
                    // Get KJV verse IDs for same (bookId, chapter)
                    val bookId = verseList.first().bookId
                    val chapter = verseList.first().chapter
                    val kjvVerses = bibleDao.getVerses("kjv", bookId, chapter)
                    val kjvVerseIds = kjvVerses.first().map { it.id }

                    // Get cross-refs for KJV verse IDs
                    val refs = crossReferenceDao.getCrossReferencesOnce(kjvVerseIds)

                    // Map from KJV verse ID → current verse ID
                    // All share the same (bookId, chapter, verse) numbering
                    val kjvToCurrent = verseList.associateBy { it.verse }

                    val result = mutableMapOf<Long, MutableList<CrossReferenceDisplay>>()
                    for (ref in refs) {
                        // Find the KJV verse number for this fromVerseId
                        val verseNum = kjvVerses.first().find { it.id == ref.fromVerseId }?.verse
                        if (verseNum != null) {
                            val currentVerse = kjvToCurrent[verseNum]
                            if (currentVerse != null) {
                                result.getOrPut(currentVerse.id) { mutableListOf() }.add(ref)
                            }
                        }
                    }
                    emit(result)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Map of verseId → highlight color for the current chapter. */
    val highlightMap: StateFlow<Map<Long, HighlightColor>> = verses
        .flatMapLatest { verseList ->
            if (verseList.isEmpty()) flowOf(emptyMap())
            else highlightDao.observeHighlightsForVerses(verseList.map { it.id })
                .map { highlights ->
                    highlights.associate { h -> h.verseId to HighlightColor.fromOrdinal(h.color) }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // ── Navigation Actions ──────────────────────────────────────

    fun selectTranslation(translationId: String) {
        _selectedTranslationId.value = translationId
    }

    fun selectBook(bookId: Int) {
        _selectedBookId.value = bookId
        _selectedChapter.value = 1
    }

    fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
    }

    // ── Split-pane actions ───────────────────────────────────────

    /** Enable/disable or switch secondary translation. null = close pane. */
    fun setSecondaryTranslation(translationId: String?) {
        // Don't allow same as primary
        if (translationId != null && translationId == _selectedTranslationId.value) return
        _selectedSecondaryTranslationId.value = translationId
    }

    fun closeSecondaryPane() {
        _selectedSecondaryTranslationId.value = null
    }

    fun nextChapter() {
        val current = _selectedChapter.value
        val chapters = chapters.value
        val maxChapter = chapters.maxOrNull() ?: current
        if (current < maxChapter) {
            _selectedChapter.value = current + 1
        }
    }

    fun previousChapter() {
        val current = _selectedChapter.value
        if (current > 1) {
            _selectedChapter.value = current - 1
        }
    }

    // ── Bookmark Actions ────────────────────────────────────────

    /** Toggle bookmark for a verse. */
    fun toggleBookmark(verseId: Long) {
        viewModelScope.launch {
            val existing = bookmarkDao.getBookmarkForVerse(verseId)
            if (existing != null) {
                bookmarkDao.deleteByVerseId(verseId)
            } else {
                bookmarkDao.insert(
                    BookmarkEntity(
                        verseId = verseId,
                        label = null,
                        createdAt = System.currentTimeMillis(),
                        tags = null
                    )
                )
            }
        }
    }

    // ── Highlight Actions ───────────────────────────────────────

    /** Toggle highlight for a verse. Removes highlight if same color. */
    fun toggleHighlight(verseId: Long, color: HighlightColor) {
        viewModelScope.launch {
            val existing = highlightDao.getHighlightsForVerses(listOf(verseId))
            val sameColor = existing.firstOrNull { it.color == color.ordinal }
            if (sameColor != null) {
                highlightDao.delete(sameColor)
            } else {
                highlightDao.deleteAllForVerse(verseId)
                highlightDao.insert(
                    HighlightEntity(
                        verseId = verseId,
                        color = color.ordinal,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // ── Reading History ─────────────────────────────────────────

    /**
     * Record that the user has read this chapter.
     * Called when the screen is displayed or the user navigates.
     */
    fun recordReading() {
        viewModelScope.launch {
            val verseId = verses.value.firstOrNull()?.id ?: return@launch
            val now = System.currentTimeMillis()
            readingHistoryDao.upsert(
                ReadingHistoryEntity(
                    verseId = verseId,
                    lastReadAt = now,
                    readCount = 1
                )
            )
            preferences.setLastReadingPosition(
                _selectedTranslationId.value,
                _selectedBookId.value,
                _selectedChapter.value
            )
        }
    }

    fun navigateToBook(bookId: Int, chapter: Int = 1) {
        _selectedBookId.value = bookId
        _selectedChapter.value = chapter
    }

    // ── Default Translation from Preferences ────────────────────

    init {
        viewModelScope.launch {
            preferences.defaultTranslation.collect { translationId ->
                _selectedTranslationId.value = translationId
            }
        }
        viewModelScope.launch {
            preferences.lastReadingPosition.collect { (translationId, bookId, chapter) ->
                translationId?.let { _selectedTranslationId.value = it }
                _selectedBookId.value = bookId
                _selectedChapter.value = chapter
            }
        }
    }
}
