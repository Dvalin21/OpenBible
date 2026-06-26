package com.openbible

import com.openbible.data.db.dao.ReadingHistoryDao
import com.openbible.data.db.dao.ReadingHistoryWithVerse
import com.openbible.data.db.dao.StrongDao
import com.openbible.data.db.entity.StrongNumberEntity
import com.openbible.data.db.entity.VerseStrongLinkEntity
import com.openbible.ui.home.HomeViewModel
import com.openbible.ui.strongs.StrongViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── HomeViewModel ─────────────────────────────────────────────

    @Test
    fun `HomeViewModel lastRead returns null when empty`() {
        val dao = mockk<ReadingHistoryDao>()
        coEvery { dao.getMostRecentReading() } returns flowOf(null)

        val vm = HomeViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.lastRead.value)
    }

    @Test
    fun `HomeViewModel lastRead returns most recent verse`() {
        val history = ReadingHistoryWithVerse(
            verseId = 1001001L, lastReadAt = 1000L, readCount = 5,
            translationId = "kjv", bookId = 1, chapter = 1,
            verseNumber = 1, text = "In the beginning...",
            bookAbbreviation = "Gen"
        )

        val dao = mockk<ReadingHistoryDao>()
        coEvery { dao.getMostRecentReading() } returns flowOf(history)

        val vm = HomeViewModel(dao)

        // stateIn with WhileSubscribed needs at least one subscriber
        val collectJob = CoroutineScope(testDispatcher).launch { vm.lastRead.collect { } }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.lastRead.value)
        assertEquals(1001001L, vm.lastRead.value!!.verseId)
        assertEquals(5, vm.lastRead.value!!.readCount)
        assertEquals("In the beginning...", vm.lastRead.value!!.text)
        collectJob.cancel()
    }

    @Test
    fun `HomeViewModel lastRead shares via StateFlow`() {
        val dao = mockk<ReadingHistoryDao>()
        coEvery { dao.getMostRecentReading() } returns flowOf(null)

        val vm = HomeViewModel(dao)
        testDispatcher.scheduler.advanceUntilIdle()

        // StateFlow should be replayable — multiple collectors get same value
        assertNull(vm.lastRead.value)           // first read
        assertNull(vm.lastRead.value)           // second read — same value
    }

    // ── StrongViewModel — Search ─────────────────────────────────

    @Test
    fun `StrongViewModel search returns matching results`() {
        val dao = mockk<StrongDao>()
        val results = listOf(
            StrongNumberEntity(
                number = "G26", lemma = "ἀγάπη", transliteration = "agapē",
                pronunciation = null, partOfSpeech = "noun",
                definition = "love", derivation = null, usageCount = 116,
                language = "Greek"
            ),
            StrongNumberEntity(
                number = "G27", lemma = "ἀγαπητός", transliteration = "agapētos",
                pronunciation = null, partOfSpeech = "adjective",
                definition = "beloved", derivation = null, usageCount = 61,
                language = "Greek"
            )
        )

        coEvery { dao.searchStrongNumbers("love") } returns results

        val vm = StrongViewModel(dao)
        vm.search("love")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.searchResults.value.size)
        assertEquals("G26", vm.searchResults.value[0].number)
        assertEquals("G27", vm.searchResults.value[1].number)
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun `StrongViewModel search empty query clears results`() {
        val dao = mockk<StrongDao>()
        val vm = StrongViewModel(dao)

        // First populate with results
        coEvery { dao.searchStrongNumbers("love") } returns listOf(
            StrongNumberEntity("G26", "ἀγάπη", "agapē", null, null, "love", null, 0, "Greek")
        )
        vm.search("love")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.searchResults.value.size)

        // Then clear with blank query
        vm.search("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.searchResults.value.isEmpty())
        assertFalse(vm.isSearching.value)
    }

    @Test
    fun `StrongViewModel search with whitespace query clears`() {
        val dao = mockk<StrongDao>()
        val vm = StrongViewModel(dao)
        vm.search("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.searchResults.value.isEmpty())
    }

    @Test
    fun `StrongViewModel search toggles isSearching`() {
        val dao = mockk<StrongDao>()
        coEvery { dao.searchStrongNumbers("word") } returns listOf(
            StrongNumberEntity("H1697", "דָּבָר", "dabar", null, "noun", "word", null, 0, "Hebrew")
        )

        val vm = StrongViewModel(dao)

        // Before search: not searching
        assertFalse(vm.isSearching.value)

        vm.search("word")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.isSearching.value)
        assertEquals("H1697", vm.searchResults.value[0].number)
    }

    // ── StrongViewModel — Detail ─────────────────────────────────

    @Test
    fun `StrongViewModel loadDetail loads entity and verse links`() {
        val dao = mockk<StrongDao>()
        val entity = StrongNumberEntity(
            "G3056", "λόγος", "logos", "log'-os", "noun",
            "a word, speech, divine utterance", "from G3004", 330, "Greek"
        )
        val links = listOf(
            VerseStrongLinkEntity(1001001L, "G3056", 0, "Λόγος", "Logos"),
            VerseStrongLinkEntity(1001001L, "G3056", 5, "λόγον", "logon")
        )

        coEvery { dao.getStrongNumber("G3056") } returns entity
        coEvery { dao.getVersesForStrongNumber("G3056") } returns links

        val vm = StrongViewModel(dao)
        vm.loadDetail("G3056")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.detail.value)
        assertEquals("G3056", vm.detail.value!!.number.number)
        assertEquals("λόγος", vm.detail.value!!.number.lemma)
        assertEquals(2, vm.detail.value!!.verseLinks.size)
    }

    @Test
    fun `StrongViewModel loadDetail with unknown number returns null`() {
        val dao = mockk<StrongDao>()
        coEvery { dao.getStrongNumber("G9999") } returns null

        val vm = StrongViewModel(dao)
        vm.loadDetail("G9999")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.detail.value)
    }

    // ── StrongViewModel — Verse Words ────────────────────────────

    @Test
    fun `StrongViewModel loadWordsForVerse loads word info`() {
        val dao = mockk<StrongDao>()
        val verseId = 1001001L
        val strongNumbers = listOf(
            StrongNumberEntity("G3056", "λόγος", "logos", null, "noun", "word", null, 0, "Greek"),
            StrongNumberEntity("G3588", "ὁ", "ho", null, "article", "the", null, 0, "Greek")
        )
        val links = listOf(
            VerseStrongLinkEntity(verseId, "G3056", 0, "Λόγος", "Logos"),
            VerseStrongLinkEntity(verseId, "G3588", 1, "ὁ", "ho")
        )

        coEvery { dao.getStrongNumbersForVerse(verseId) } returns strongNumbers
        coEvery { dao.getLinksForVerse(verseId) } returns links

        val vm = StrongViewModel(dao)
        vm.loadWordsForVerse(verseId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.verseWords.value.size)
        assertEquals("Λόγος", vm.verseWords.value[0].originalWord)
        assertEquals("G3056", vm.verseWords.value[0].strongNumber.number)
        assertEquals("ὁ", vm.verseWords.value[1].originalWord)
        assertFalse(vm.isLoadingVerseWords.value)
    }

    @Test
    fun `StrongViewModel clearVerseWords resets state`() {
        val dao = mockk<StrongDao>()
        val vm = StrongViewModel(dao)

        // Populate
        coEvery { dao.getStrongNumbersForVerse(any()) } returns emptyList()
        coEvery { dao.getLinksForVerse(any()) } returns emptyList()
        vm.loadWordsForVerse(1001001L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Clear
        vm.clearVerseWords()
        assertTrue(vm.verseWords.value.isEmpty())
    }

    @Test
    fun `StrongViewModel loadWordsForVerse with no matches returns empty`() {
        val dao = mockk<StrongDao>()
        coEvery { dao.getStrongNumbersForVerse(any()) } returns emptyList()
        coEvery { dao.getLinksForVerse(any()) } returns emptyList()

        val vm = StrongViewModel(dao)
        vm.loadWordsForVerse(99999L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.verseWords.value.isEmpty())
        assertFalse(vm.isLoadingVerseWords.value)
    }
}
