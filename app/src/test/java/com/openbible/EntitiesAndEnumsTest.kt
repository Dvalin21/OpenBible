package com.openbible

import com.openbible.data.db.converter.Converters
import com.openbible.data.db.entity.*
import com.openbible.data.model.HighlightColor
import com.openbible.data.model.PenMode
import com.openbible.data.model.Testament
import com.openbible.data.model.ThemeMode
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for all 18 Room entities, 4 enums, and type converters.
 *
 * Verifies:
 * - All entities construct correctly with required fields
 * - All enum values exist and have expected properties
 * - Room TypeConverters serialize/deserialize correctly
 * - HighlightColor utility methods work
 */
class EntitiesAndEnumsTest {

    // ── Enums ──────────────────────────────────────────────────────

    @Test
    fun testament_has_old_and_new() {
        assertEquals(Testament.OLD, Testament.valueOf("OLD"))
        assertEquals(Testament.NEW, Testament.valueOf("NEW"))
        assertTrue(Testament.entries.containsAll(listOf(Testament.OLD, Testament.NEW)))
    }

    @Test
    fun themeMode_has_light_dark_sepia_auto() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
        assertEquals(ThemeMode.SEPIA, ThemeMode.valueOf("SEPIA"))
        assertEquals(ThemeMode.AUTO_TIME, ThemeMode.valueOf("AUTO_TIME"))
        assertEquals(4, ThemeMode.entries.size)
    }

    @Test
    fun autoTime_resolves_to_correct_theme_by_hour() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.AUTO_TIME.resolve(6))
        assertEquals(ThemeMode.LIGHT, ThemeMode.AUTO_TIME.resolve(12))
        assertEquals(ThemeMode.LIGHT, ThemeMode.AUTO_TIME.resolve(16))
        assertEquals(ThemeMode.SEPIA, ThemeMode.AUTO_TIME.resolve(17))
        assertEquals(ThemeMode.SEPIA, ThemeMode.AUTO_TIME.resolve(19))
        assertEquals(ThemeMode.DARK, ThemeMode.AUTO_TIME.resolve(20))
        assertEquals(ThemeMode.DARK, ThemeMode.AUTO_TIME.resolve(23))
        assertEquals(ThemeMode.DARK, ThemeMode.AUTO_TIME.resolve(0))
        assertEquals(ThemeMode.DARK, ThemeMode.AUTO_TIME.resolve(5))
        // Non-auto modes resolve to themselves
        assertEquals(ThemeMode.LIGHT, ThemeMode.LIGHT.resolve(12))
        assertEquals(ThemeMode.DARK, ThemeMode.DARK.resolve(12))
    }

    @Test
    fun penMode_has_text_ink_both() {
        assertEquals(PenMode.TEXT, PenMode.valueOf("TEXT"))
        assertEquals(PenMode.INK, PenMode.valueOf("INK"))
        assertEquals(PenMode.BOTH, PenMode.valueOf("BOTH"))
        assertEquals(3, PenMode.entries.size)
    }

    @Test
    fun highlightColor_has_five_colors() {
        assertEquals(5, HighlightColor.entries.size)
        assertEquals(HighlightColor.YELLOW, HighlightColor.valueOf("YELLOW"))
        assertEquals(HighlightColor.GREEN, HighlightColor.valueOf("GREEN"))
        assertEquals(HighlightColor.BLUE, HighlightColor.valueOf("BLUE"))
        assertEquals(HighlightColor.PINK, HighlightColor.valueOf("PINK"))
        assertEquals(HighlightColor.ORANGE, HighlightColor.valueOf("ORANGE"))
    }

    @Test
    fun highlightColor_fromOrdinal_returns_correct() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromOrdinal(0))
        assertEquals(HighlightColor.GREEN, HighlightColor.fromOrdinal(1))
        assertEquals(HighlightColor.BLUE, HighlightColor.fromOrdinal(2))
        assertEquals(HighlightColor.PINK, HighlightColor.fromOrdinal(3))
        assertEquals(HighlightColor.ORANGE, HighlightColor.fromOrdinal(4))
    }

    @Test
    fun highlightColor_fromOrdinal_fallback_on_invalid() {
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromOrdinal(-1))
        assertEquals(HighlightColor.YELLOW, HighlightColor.fromOrdinal(99))
    }

    @Test
    fun highlightColor_has_valid_argb_values() {
        assertTrue(HighlightColor.YELLOW.argb and 0xFF000000 == 0xFF000000L) // fully opaque
        assertTrue(HighlightColor.GREEN.argb and 0xFF000000 == 0xFF000000L)
        assertTrue(HighlightColor.BLUE.argb and 0xFF000000 == 0xFF000000L)
        assertTrue(HighlightColor.PINK.argb and 0xFF000000 == 0xFF000000L)
        assertTrue(HighlightColor.ORANGE.argb and 0xFF000000 == 0xFF000000L)
    }

    // ── Type Converters ────────────────────────────────────────────

    private val converters = Converters()

    @Test
    fun converter_testament_roundtrip() {
        for (t in Testament.entries) {
            val str = converters.testamentToString(t)
            val back = converters.stringToTestament(str)
            assertEquals(t, back)
        }
    }

    @Test
    fun converter_penMode_roundtrip() {
        for (p in PenMode.entries) {
            val str = converters.penModeToString(p)
            val back = converters.stringToPenMode(str)
            assertEquals(p, back)
        }
    }

    // ── TranslationEntity (1) ─────────────────────────────────────

    @Test
    fun translationEntity_construct() {
        val t = TranslationEntity(
            id = "kjv",
            name = "King James Version",
            abbreviation = "KJV",
            language = "en",
            copyright = null,
            isPublicDomain = true,
            isBundled = true
        )
        assertEquals("kjv", t.id)
        assertEquals("King James Version", t.name)
        assertEquals("KJV", t.abbreviation)
        assertEquals("en", t.language)
        assertNull(t.copyright)
        assertTrue(t.isPublicDomain)
        assertTrue(t.isBundled)
    }

    @Test
    fun translationEntity_with_copyright() {
        val t = TranslationEntity(
            id = "nkjv", name = "New King James Version", abbreviation = "NKJV",
            language = "en", copyright = "Copyright © 1982 Thomas Nelson",
            isPublicDomain = false, isBundled = true
        )
        assertFalse(t.isPublicDomain)
        assertNotNull(t.copyright)
    }

    // ── BookEntity (2) ────────────────────────────────────────────

    @Test
    fun bookEntity_construct() {
        val b = BookEntity(
            id = 101, translationId = "kjv", name = "Genesis",
            abbreviation = "Gen", number = 1, chapterCount = 50,
            testament = Testament.OLD, totalVerses = 1533
        )
        assertEquals("Genesis", b.name)
        assertEquals(Testament.OLD, b.testament)
        assertEquals(50, b.chapterCount)
    }

    // ── VerseEntity (3) ───────────────────────────────────────────

    @Test
    fun verseEntity_construct() {
        val v = VerseEntity(
            id = 1001001L, translationId = "kjv", bookId = 1,
            chapter = 1, verse = 1, text = "In the beginning God created the heaven and the earth."
        )
        assertEquals("kjv", v.translationId)
        assertEquals(1, v.chapter)
        assertEquals(1, v.verse)
        assertTrue(v.text.startsWith("In the beginning"))
    }

    // ── CrossReferenceEntity (4) ──────────────────────────────────

    @Test
    fun crossReferenceEntity_construct() {
        val cr = CrossReferenceEntity(
            id = 1, fromVerseId = 1001001L, toBookId = 2,
            toChapter = 1, toVerseStart = 1, toVerseEnd = null, relevance = 5
        )
        assertEquals(1001001L, cr.fromVerseId)
        assertEquals(2, cr.toBookId)
        assertNull(cr.toVerseEnd)
        assertEquals(5, cr.relevance)
    }

    // ── BookmarkEntity (5) ────────────────────────────────────────

    @Test
    fun bookmarkEntity_construct() {
        val bm = BookmarkEntity(
            verseId = 1001001L, label = "Creation", createdAt = 1000L, tags = "creation,genesis"
        )
        assertEquals(1001001L, bm.verseId)
        assertEquals("Creation", bm.label)
        assertEquals("creation,genesis", bm.tags)
        assertEquals(0, bm.id) // auto-generated default
    }

    // ── HighlightEntity (6) ───────────────────────────────────────

    @Test
    fun highlightEntity_construct() {
        val h = HighlightEntity(verseId = 1001001L, color = 0, createdAt = 1000L)
        assertEquals(1001001L, h.verseId)
        assertEquals(0, h.id) // auto-generated default
    }

    // ── NoteEntity (7) ────────────────────────────────────────────

    @Test
    fun noteEntity_construct_text() {
        val n = NoteEntity(
            notebookId = 1L, title = "Sermon Notes",
            contentText = "Great sermon today...",
            penStrokes = null,
            penMode = PenMode.TEXT,
            createdAt = 1000L, updatedAt = 1000L,
            tags = null, color = null
        )
        assertEquals("Sermon Notes", n.title)
        assertEquals(PenMode.TEXT, n.penMode)
        assertNull(n.penStrokes)
    }

    @Test
    fun noteEntity_construct_ink() {
        val n = NoteEntity(
            notebookId = null, title = "Drawing",
            contentText = null,
            penStrokes = "[{\"points\":[]}]",
            penMode = PenMode.INK,
            createdAt = 1000L, updatedAt = 1000L,
            tags = null, color = null
        )
        assertNull(n.notebookId)
        assertEquals(PenMode.INK, n.penMode)
        assertNotNull(n.penStrokes)
    }

    // ── NoteImageEntity (8) ───────────────────────────────────────

    @Test
    fun noteImageEntity_construct() {
        val img = NoteImageEntity(
            noteId = 1L, filePath = "/data/notes/image.jpg",
            caption = "Whiteboard photo", position = 0
        )
        assertEquals("/data/notes/image.jpg", img.filePath)
        assertEquals("Whiteboard photo", img.caption)
        assertEquals(0, img.position)
    }

    // ── NoteVerseLinkEntity (9) ───────────────────────────────────

    @Test
    fun noteVerseLinkEntity_construct() {
        val link = NoteVerseLinkEntity(noteId = 1L, verseId = 1001001L)
        assertEquals(1L, link.noteId)
        assertEquals(1001001L, link.verseId)
    }

    // ── NotebookEntity (10) ───────────────────────────────────────

    @Test
    fun notebookEntity_construct() {
        val nb = NotebookEntity(name = "Sermons", color = 0xFF0000, icon = "book", createdAt = 1000L)
        assertEquals("Sermons", nb.name)
        assertEquals("book", nb.icon)
    }

    // ── ReadingPlanEntity (11) ────────────────────────────────────

    @Test
    fun readingPlanEntity_construct() {
        val rp = ReadingPlanEntity(
            name = "Bible in a Year", description = "Read through",
            durationDays = 365, isPrebuilt = true
        )
        assertEquals(365, rp.durationDays)
        assertTrue(rp.isPrebuilt)
    }

    // ── ReadingPlanDayEntity (12) ─────────────────────────────────

    @Test
    fun readingPlanDayEntity_construct() {
        val day = ReadingPlanDayEntity(
            planId = 1L, dayNumber = 1, title = "Day 1",
            readings = """[{"bookId":1,"chapter":1}]"""
        )
        assertEquals(1, day.dayNumber)
        assertTrue(day.readings.contains("bookId"))
    }

    // ── ReadingProgressEntity (13) ────────────────────────────────

    @Test
    fun readingProgressEntity_construct() {
        val prog = ReadingProgressEntity(
            planId = 1L, dayNumber = 1, completed = true, completedAt = 1000L
        )
        assertTrue(prog.completed)
        assertEquals(1000L, prog.completedAt)
    }

    // ── ReadingHistoryEntity (14) ─────────────────────────────────

    @Test
    fun readingHistoryEntity_construct() {
        val rh = ReadingHistoryEntity(verseId = 1001001L, lastReadAt = 1000L, readCount = 5)
        assertEquals(1001001L, rh.verseId)
        assertEquals(5, rh.readCount)
    }

    // ── StrongNumberEntity (15) ───────────────────────────────────

    @Test
    fun strongNumberEntity_construct_greek() {
        val s = StrongNumberEntity(
            number = "G3056", lemma = "λόγος", transliteration = "logos",
            pronunciation = "log'-os", partOfSpeech = "noun",
            definition = "a word, speech, divine utterance",
            derivation = "from G3004", usageCount = 330, language = "Greek"
        )
        assertEquals("G3056", s.number)
        assertEquals("λόγος", s.lemma)
        assertEquals("logos", s.transliteration)
        assertEquals("noun", s.partOfSpeech)
    }

    @Test
    fun strongNumberEntity_construct_hebrew() {
        val s = StrongNumberEntity(
            number = "H7225", lemma = "רֵאשִׁית", transliteration = "reshith",
            pronunciation = null, partOfSpeech = "noun",
            definition = "beginning, chief",
            derivation = null, usageCount = 51, language = "Hebrew"
        )
        assertEquals("H7225", s.number)
        assertNull(s.pronunciation)
        assertNull(s.derivation)
    }

    // ── VerseStrongLinkEntity (16) ────────────────────────────────

    @Test
    fun verseStrongLinkEntity_construct() {
        val link = VerseStrongLinkEntity(
            verseId = 1001001L, strongNumber = "G3056",
            wordPosition = 0, originalWord = "λόγος", transliteration = "logos"
        )
        assertEquals(1001001L, link.verseId)
        assertEquals("G3056", link.strongNumber)
        assertEquals(0, link.wordPosition)
    }

    // ── BibleLocationEntity (17) ──────────────────────────────────

    @Test
    fun bibleLocationEntity_construct() {
        val loc = BibleLocationEntity(
            id = "jerusalem", name = "Jerusalem",
            modernName = "Al-Quds", latitude = 31.7683, longitude = 35.2137,
            description = "Capital of Israel", category = "city",
            significance = "City of David"
        )
        assertEquals("jerusalem", loc.id)
        assertEquals(31.7683, loc.latitude, 0.0001)
        assertEquals(35.2137, loc.longitude, 0.0001)
        assertEquals("city", loc.category)
    }

    @Test
    fun bibleLocationEntity_modernName_nullable() {
        val loc = BibleLocationEntity(
            id = "eden", name = "Garden of Eden",
            modernName = null, latitude = 0.0, longitude = 0.0,
            description = "Biblical garden", category = "region",
            significance = null
        )
        assertNull(loc.modernName)
        assertNull(loc.significance)
    }

    // ── VerseLocationLinkEntity (18) ──────────────────────────────

    @Test
    fun verseLocationLinkEntity_construct() {
        val link = VerseLocationLinkEntity(locationId = "jerusalem", verseId = 1001001L)
        assertEquals("jerusalem", link.locationId)
        assertEquals(1001001L, link.verseId)
    }
}
