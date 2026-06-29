package com.openbible.ui.strongs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.StrongDao
import com.openbible.data.db.dao.VerseLinkWithReference
import com.openbible.data.db.entity.StrongNumberEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StrongNumberWithVerses(
    val number: StrongNumberEntity,
    val verseLinks: List<VerseLinkWithReference>
)

data class WordStrongInfo(
    val originalWord: String,
    val transliteration: String?,
    val strongNumber: StrongNumberEntity
)

@HiltViewModel
class StrongViewModel @Inject constructor(
    private val strongDao: StrongDao,
    private val bibleDao: BibleDao
) : ViewModel() {

    // ── Search ───────────────────────────────────────────────────

    private val _searchResults = MutableStateFlow<List<StrongNumberEntity>>(emptyList())
    val searchResults: StateFlow<List<StrongNumberEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        viewModelScope.launch {
            _searchResults.value = strongDao.searchStrongNumbers(query.trim())
            _isSearching.value = false
        }
    }

    // ── Detail ───────────────────────────────────────────────────

    private val _detail = MutableStateFlow<StrongNumberWithVerses?>(null)
    val detail: StateFlow<StrongNumberWithVerses?> = _detail.asStateFlow()

    fun loadDetail(number: String) {
        viewModelScope.launch {
            val entity = strongDao.getStrongNumber(number) ?: return@launch
            val links = strongDao.getVerseLinksWithReference(number)
            _detail.value = StrongNumberWithVerses(entity, links)
        }
    }

    // ── Verse word lookup ────────────────────────────────────────

    private val _verseWords = MutableStateFlow<List<WordStrongInfo>>(emptyList())
    val verseWords: StateFlow<List<WordStrongInfo>> = _verseWords.asStateFlow()

    private val _isLoadingVerseWords = MutableStateFlow(false)
    val isLoadingVerseWords: StateFlow<Boolean> = _isLoadingVerseWords.asStateFlow()

    fun loadWordsForVerse(verseId: Long) {
        _isLoadingVerseWords.value = true
        viewModelScope.launch {
            val numbers = strongDao.getStrongNumbersForVerse(verseId)
            val links = strongDao.getLinksForVerse(verseId)
            val words = links.mapNotNull { link ->
                val num = numbers.find { it.number == link.strongNumber }
                if (num != null) WordStrongInfo(link.originalWord, link.transliteration, num)
                else null
            }
            _verseWords.value = words
            _isLoadingVerseWords.value = false
        }
    }

    fun clearVerseWords() {
        _verseWords.value = emptyList()
    }
}
