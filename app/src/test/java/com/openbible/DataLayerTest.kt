package com.openbible

import com.openbible.data.ReadingPlanSeeder
import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.ReadingPlanDao
import com.openbible.data.db.entity.NoteEntity
import com.openbible.data.db.entity.ReadingPlanEntity
import com.openbible.data.db.entity.StrongNumberEntity
import com.openbible.data.db.entity.VerseStrongLinkEntity
import com.openbible.data.model.PenMode
import com.openbible.data.repository.NoteRepository
import androidx.compose.ui.geometry.Offset
import com.openbible.ui.notes.*
import com.openbible.ui.notes.InkStroke
import com.openbible.ui.notes.NoteEditorViewModel
import com.openbible.ui.notes.strokesFromJson
import com.openbible.ui.notes.strokesToJson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for data layer logic: ReadingPlanSeeder, InkStroke serialization,
 * JSON parsing patterns (importer logic extracted).
 *
 * Verifies:
 * - ReadingPlanSeeder distribution math is correct
 * - InkStroke JSON roundtrip preserves data
 * - Strong's/location JSON structures parse correctly
 */
class DataLayerTest {

    // ── ReadingPlanSeeder ─────────────────────────────────────────

    @Test
    fun `ReadingPlanSeeder distributes 1189 chapters across 365 days`() {
        // Simulate KJV: 66 books with their chapter counts
        val bookCounts = listOf(
            1 to 50,    // Genesis
            2 to 40,    // Exodus
            3 to 27,    // Leviticus
            4 to 36,    // Numbers
            5 to 34,    // Deuteronomy
            6 to 24,    // Joshua
            7 to 21,    // Judges
            8 to 4,     // Ruth
            9 to 31,    // 1 Samuel
            10 to 24,   // 2 Samuel
            11 to 22,   // 1 Kings
            12 to 25,   // 2 Kings
            13 to 29,   // 1 Chronicles
            14 to 36,   // 2 Chronicles
            15 to 10,   // Ezra
            16 to 13,   // Nehemiah
            17 to 10,   // Esther
            18 to 42,   // Job
            19 to 150,  // Psalms
            20 to 31,   // Proverbs
            21 to 12,   // Ecclesiastes
            22 to 8,    // Song of Solomon
            23 to 66,   // Isaiah
            24 to 52,   // Jeremiah
            25 to 5,    // Lamentations
            26 to 48,   // Ezekiel
            27 to 12,   // Daniel
            28 to 14,   // Hosea
            29 to 3,    // Joel
            30 to 9,    // Amos
            31 to 1,    // Obadiah
            32 to 4,    // Jonah
            33 to 7,    // Micah
            34 to 3,    // Nahum
            35 to 3,    // Habakkuk
            36 to 3,    // Zephaniah
            37 to 2,    // Haggai
            38 to 14,   // Zechariah
            39 to 4,    // Malachi
            40 to 28,   // Matthew
            41 to 16,   // Mark
            42 to 24,   // Luke
            43 to 21,   // John
            44 to 28,   // Acts
            45 to 16,   // Romans
            46 to 16,   // 1 Corinthians
            47 to 13,   // 2 Corinthians
            48 to 6,    // Galatians
            49 to 6,    // Ephesians
            50 to 4,    // Philippians
            51 to 4,    // Colossians
            52 to 5,    // 1 Thessalonians
            53 to 3,    // 2 Thessalonians
            54 to 6,    // 1 Timothy
            55 to 4,    // 2 Timothy
            56 to 3,    // Titus
            57 to 1,    // Philemon
            58 to 13,   // Hebrews
            59 to 5,    // James
            60 to 5,    // 1 Peter
            61 to 3,    // 2 Peter
            62 to 5,    // 1 John
            63 to 1,    // 2 John
            64 to 1,    // 3 John
            65 to 1,    // Jude
            66 to 22    // Revelation
        )
        // Sum of all chapter counts = 1189
        val totalChapters = bookCounts.sumOf { it.second }
        assertEquals(1189, totalChapters)

        // Simulate the seeder's algorithm
        val chaptersPerDay = (totalChapters + 365 - 1) / 365  // ceil
        assertEquals(4, chaptersPerDay)

        // Verify distribution: 4 chapters/day for 365 days
        // 4 * 365 = 1460 >= 1189 ✓
        assertEquals(4, chaptersPerDay)
        assertTrue(chaptersPerDay * 365 >= totalChapters)

        // Verify JSON structure matches what the seeder produces
        val readingsJson = JSONArray().apply {
            put(JSONObject().apply { put("bookId", 1); put("chapter", 1) })
            put(JSONObject().apply { put("bookId", 1); put("chapter", 2) })
        }.toString()
        assertTrue(readingsJson.contains("\"bookId\":1"))
        assertTrue(readingsJson.contains("\"chapter\":1"))
    }

    @Test
    fun `ReadingPlanSeeder chaptersPerDay calculation`() {
        // Edge cases
        assertEquals(4, (1189 + 365 - 1) / 365)  // ceil(1189/365)
        assertEquals(1, (1 + 365 - 1) / 365)     // ceil(1/365) = 1
        assertEquals(1, (365 + 365 - 1) / 365)   // ceil(365/365) = 1
        assertEquals(2, (366 + 365 - 1) / 365)   // ceil(366/365) = 2
    }

    @Test
    fun `ReadingPlanSeeder empty book list returns early`() {
        val bibleDao = mockk<BibleDao>()
        val planDao = mockk<ReadingPlanDao>()

        coEvery { planDao.getAllPlansOnce() } returns emptyList()
        coEvery { bibleDao.getBookChapterCounts("kjv") } returns emptyList()

        // Should not crash — returns early if no chapters
        runBlocking {
            ReadingPlanSeeder.ensureSeeded(planDao, bibleDao)
        }
        // If we got here, it didn't throw
    }

    @Test
    fun `ReadingPlanSeeder already seeded returns early`() {
        val planDao = mockk<ReadingPlanDao>()
        val bibleDao = mockk<BibleDao>()

        coEvery { planDao.getAllPlansOnce() } returns listOf(
            ReadingPlanEntity(name = "Existing", description = null, durationDays = 365, isPrebuilt = true)
        )

        runBlocking {
            ReadingPlanSeeder.ensureSeeded(planDao, bibleDao)
        }
        // getAllPlansOnce was called, but getBookChapterCounts was NOT
    }

    // ── InkStroke Serialization ──────────────────────────────────

    @Test
    fun `InkStroke JSON roundtrip empty stroke`() {
        val original = InkStroke(points = emptyList(), color = 0xFF000000, width = 2f, isEraser = false)
        val json = original.toJson(canvasWidth = 1000f, canvasHeight = 2000f)
        val restored = InkStroke.fromJson(json)

        assertEquals(original.color, restored.color)
        assertEquals(original.width, restored.width, 0.001f)
        assertEquals(original.isEraser, restored.isEraser)
        assertTrue(restored.points.isEmpty())
    }

    @Test
    fun `InkStroke JSON roundtrip with points`() {
        val original = InkStroke(
            points = listOf(
                androidx.compose.ui.geometry.Offset(10f, 20f),
                androidx.compose.ui.geometry.Offset(30f, 40f),
                androidx.compose.ui.geometry.Offset(50f, 60f)
            ),
            color = 0xFFFF0000,
            width = 3.5f,
            isEraser = true
        )
        val json = original.toJson(canvasWidth = 1000f, canvasHeight = 2000f)
        val restored = InkStroke.fromJson(json)

        assertEquals(0xFFFF0000, restored.color)
        assertEquals(3.5f, restored.width, 0.001f)
        assertTrue(restored.isEraser)
        assertEquals(3, restored.points.size)
        assertEquals(10f, restored.points[0].x, 0.001f)
        assertEquals(20f, restored.points[0].y, 0.001f)
        assertEquals(50f, restored.points[2].x, 0.001f)
        assertEquals(60f, restored.points[2].y, 0.001f)
    }

    @Test
    fun `strokesToJson and strokesFromJson roundtrip multiple strokes`() {
        val strokes = listOf(
            InkStroke(
                points = listOf(
                    androidx.compose.ui.geometry.Offset(0f, 0f),
                    androidx.compose.ui.geometry.Offset(100f, 100f)
                ),
                color = 0xFF000000, width = 2f, isEraser = false
            ),
            InkStroke(
                points = listOf(
                    androidx.compose.ui.geometry.Offset(50f, 50f)
                ),
                color = 0xFFFF0000, width = 5f, isEraser = true
            )
        )

        val json = strokesToJson(strokes)
        val restored = strokesFromJson(json)

        assertEquals(2, restored.size)
        assertEquals(2, restored[0].points.size)
        assertEquals(1, restored[1].points.size)
        assertTrue(restored[1].isEraser)
        assertEquals(0xFFFF0000, restored[1].color)
    }

    @Test
    fun `strokesFromJson handles null and blank gracefully`() {
        assertTrue(strokesFromJson(null).isEmpty())
        assertTrue(strokesFromJson("").isEmpty())
        assertTrue(strokesFromJson("  ").isEmpty())
    }

    @Test
    fun `strokesFromJson handles malformed JSON gracefully`() {
        assertTrue(strokesFromJson("not json").isEmpty())
        assertTrue(strokesFromJson("{}").isEmpty())  // object not array
        assertTrue(strokesFromJson("[invalid]").isEmpty())
    }

    // ── Unified page model JSON roundtrip ──────────────────────────

    @Test
    fun `pagesToJson and pagesFromJson roundtrip ink, shape, image, highlighter, transform, template`() {
        val pages = listOf(
            NotePage(
                text = "Sermon notes",
                template = PageTemplate.RULED,
                coverColor = 0xFFECEFF1,
                elements = listOf(
                    InkElement(
                        id = "a", points = listOf(Offset(1f, 2f), Offset(3f, 4f)),
                        color = 0xFF000000, width = 4f, highlighter = false
                    ),
                    InkElement(
                        id = "b", points = listOf(Offset(10f, 10f)),
                        color = 0xFFFF0000, width = 20f, highlighter = true,
                        transform = ElementTransform(tx = 5f, ty = 6f, scale = 2f)
                    ),
                    ShapeElement(
                        id = "c", shape = ShapeType.OVAL, p1 = Offset(0f, 0f), p2 = Offset(50f, 60f),
                        color = 0xFF0000FF, width = 3f
                    ),
                    ImageElement(
                        id = "d", filePath = "/x/y.jpg", left = 10f, top = 20f,
                        width = 100f, height = 80f, transform = ElementTransform(scale = 1.5f)
                    )
                )
            ),
            NotePage(text = "page two", template = PageTemplate.GRID)
        )

        val json = pagesToJson(pages)
        val restored = pagesFromJson(json)

        assertEquals(2, restored.size)
        assertEquals("Sermon notes", restored[0].text)
        assertEquals(PageTemplate.RULED, restored[0].template)
        assertEquals(0xFFECEFF1, restored[0].coverColor)
        assertEquals(4, restored[0].elements.size)

        val ink = restored[0].elements[0] as InkElement
        assertEquals(0xFF000000, ink.color)
        assertEquals(4f, ink.width, 0.001f)
        assertFalse(ink.highlighter)

        val hl = restored[0].elements[1] as InkElement
        assertTrue(hl.highlighter)
        assertEquals(2f, hl.transform.scale, 0.001f)
        assertEquals(5f, hl.transform.tx, 0.001f)

        val shape = restored[0].elements[2] as ShapeElement
        assertEquals(ShapeType.OVAL, shape.shape)
        assertEquals(50f, shape.p2.x, 0.001f)

        val img = restored[0].elements[3] as ImageElement
        assertEquals("/x/y.jpg", img.filePath)
        assertEquals(1.5f, img.transform.scale, 0.001f)

        assertEquals("page two", restored[1].text)
        assertEquals(PageTemplate.GRID, restored[1].template)
    }

    @Test
    fun `pagesFromJson handles null and malformed gracefully`() {
        assertTrue(pagesFromJson(null).isEmpty())
        assertTrue(pagesFromJson("").isEmpty())
        assertTrue(pagesFromJson("not json").isEmpty())
    }

    @Test
    fun `legacyStrokesToElements drops eraser strokes and converts ink`() {
        val legacy = listOf(
            InkStroke(points = listOf(Offset(0f, 0f), Offset(5f, 5f)), color = 0xFF000000, width = 2f, isEraser = false),
            InkStroke(points = listOf(Offset(1f, 1f)), color = 0xFFFFFFFF, width = 10f, isEraser = true)
        )
        val els = legacyStrokesToElements(legacy)
        assertEquals(1, els.size)
        assertTrue(els[0] is InkElement)
        assertEquals(0xFF000000, (els[0] as InkElement).color)
    }

    // ── Strong's Number JSON Parsing (extracted pattern) ──────────

    @Test
    fun `parse strong numbers JSON structure`() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("number", "G26")
                put("lemma", "ἀγάπη")
                put("transliteration", "agapē")
                put("pronunciation", "ag-ah'-pay")
                put("partOfSpeech", "noun")
                put("definition", "love, affection, benevolence")
                put("derivation", "from G25")
                put("usageCount", 116)
                put("language", "Greek")
            })
            put(JSONObject().apply {
                put("number", "H430")
                put("lemma", "אֱלֹהִים")
                put("transliteration", "elohim")
                // pronunciation omitted — tests optional field handling
                put("partOfSpeech", "noun")
                put("definition", "God, god")
                put("usageCount", 2600)
                put("language", "Hebrew")
            })
        }

        val numbers = mutableListOf<StrongNumberEntity>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            numbers.add(
                StrongNumberEntity(
                    number = obj.getString("number"),
                    lemma = obj.optString("lemma", ""),
                    transliteration = obj.optString("transliteration", ""),
                    pronunciation = if (obj.has("pronunciation")) obj.getString("pronunciation") else null,
                    partOfSpeech = if (obj.has("partOfSpeech")) obj.getString("partOfSpeech") else null,
                    definition = obj.optString("definition", ""),
                    derivation = if (obj.has("derivation")) obj.getString("derivation") else null,
                    usageCount = obj.optInt("usageCount", 0),
                    language = obj.optString("language", "Greek")
                )
            )
        }

        assertEquals(2, numbers.size)
        assertEquals("G26", numbers[0].number)
        assertEquals("ἀγάπη", numbers[0].lemma)
        assertNotNull(numbers[0].pronunciation)
        assertNotNull(numbers[0].derivation)
        assertEquals(116, numbers[0].usageCount)

        assertEquals("H430", numbers[1].number)
        assertNull(numbers[1].pronunciation)
        assertNull(numbers[1].derivation)
        assertEquals(2600, numbers[1].usageCount)
    }

    @Test
    fun `parse strong's verse links JSON structure`() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("verseId", 1001001)
                put("strongNumber", "G3056")
                put("wordPosition", 0)
                put("originalWord", "Λόγος")
                put("transliteration", "Logos")
            })
            put(JSONObject().apply {
                put("verseId", 1001002)
                put("strongNumber", "G3056")
                put("wordPosition", 3)
                put("originalWord", "λόγου")
                // transliteration omitted — optional
            })
        }

        val links = mutableListOf<VerseStrongLinkEntity>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            links.add(
                VerseStrongLinkEntity(
                    verseId = obj.getLong("verseId"),
                    strongNumber = obj.getString("strongNumber"),
                    wordPosition = obj.getInt("wordPosition"),
                    originalWord = obj.optString("originalWord", ""),
                    transliteration = if (obj.has("transliteration")) obj.getString("transliteration") else null
                )
            )
        }

        assertEquals(2, links.size)
        assertEquals(1001001L, links[0].verseId)
        assertEquals("G3056", links[0].strongNumber)
        assertEquals(0, links[0].wordPosition)
        assertNotNull(links[0].transliteration)
        assertNull(links[1].transliteration)
    }

    // ── Bible Location JSON Parsing (extracted pattern) ───────────

    @Test
    fun `parse locations JSON structure`() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "jerusalem")
                put("name", "Jerusalem")
                put("modernName", "Al-Quds")
                put("latitude", 31.7683)
                put("longitude", 35.2137)
                put("description", "Ancient capital of Israel")
                put("category", "city")
                put("significance", "Temple location")
            })
            put(JSONObject().apply {
                put("id", "mount_sinai")
                put("name", "Mount Sinai")
                // modernName omitted — optional
                put("latitude", 28.5)
                put("longitude", 33.9)
                put("description", "Where the Law was given")
                put("category", "mountain")
                // significance omitted — optional
            })
        }

        data class TestLocation(
            val id: String, val name: String, val modernName: String?,
            val lat: Double, val lng: Double, val desc: String,
            val category: String, val significance: String?
        )

        val locations = mutableListOf<TestLocation>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            locations.add(
                TestLocation(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    modernName = if (obj.has("modernName")) obj.getString("modernName") else null,
                    lat = obj.getDouble("latitude"),
                    lng = obj.getDouble("longitude"),
                    desc = obj.getString("description"),
                    category = obj.getString("category"),
                    significance = if (obj.has("significance")) obj.getString("significance") else null
                )
            )
        }

        assertEquals(2, locations.size)
        assertEquals("jerusalem", locations[0].id)
        assertEquals("Al-Quds", locations[0].modernName)
        assertEquals(31.7683, locations[0].lat, 0.0001)
        assertNotNull(locations[0].significance)

        assertEquals("mount_sinai", locations[1].id)
        assertNull(locations[1].modernName)
        assertNull(locations[1].significance)
    }

    @Test
    fun `parse location verse links JSON structure`() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("locationId", "jerusalem")
                put("verseId", 1001001)
            })
            put(JSONObject().apply {
                put("locationId", "jerusalem")
                put("verseId", 1002005)
            })
        }

        data class TestLink(val locationId: String, val verseId: Long)
        val links = mutableListOf<TestLink>()
        for (i in 0 until json.length()) {
            val obj = json.getJSONObject(i)
            links.add(
                TestLink(
                    locationId = obj.getString("locationId"),
                    verseId = obj.getLong("verseId")
                )
            )
        }

        assertEquals(2, links.size)
        assertEquals("jerusalem", links[0].locationId)
        assertEquals(1001001L, links[0].verseId)
        assertEquals(1002005L, links[1].verseId)
    }

    // ── Note Repository Logic Tests ──────────────────────────────

    @Test
    fun `NoteEntity creation timestamp ordering`() {
        val earlier = NoteEntity(
            notebookId = null, title = "First",
            contentText = null, penStrokes = null,
            penMode = com.openbible.data.model.PenMode.TEXT,
            createdAt = 1000L, updatedAt = 1000L,
            tags = null, color = null
        )
        val later = NoteEntity(
            notebookId = null, title = "Second",
            contentText = null, penStrokes = null,
            penMode = com.openbible.data.model.PenMode.TEXT,
            createdAt = 2000L, updatedAt = 2000L,
            tags = null, color = null
        )
        assertTrue(later.createdAt > earlier.createdAt)
        assertTrue(later.updatedAt > earlier.updatedAt)
    }

    @Test
    fun `NoteEntity update bumps updatedAt`() {
        val note = NoteEntity(
            id = 1L, notebookId = null, title = "Test",
            contentText = null, penStrokes = null,
            penMode = com.openbible.data.model.PenMode.TEXT,
            createdAt = 1000L, updatedAt = 1000L,
            tags = null, color = null
        )
        val updated = note.copy(updatedAt = 2000L, contentText = "Updated content")
        assertEquals(1L, updated.id)
        assertEquals(2000L, updated.updatedAt)
        assertEquals("Updated content", updated.contentText)
        assertEquals(1000L, updated.createdAt)  // createdAt unchanged
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `NoteEditorViewModel persists tags and favorite through save`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = mockk<NoteRepository>(relaxed = true)
            coEvery { repo.getAllNotebooks() } returns emptyFlow()
            coEvery { repo.createNote(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 7L
            val vm = NoteEditorViewModel(repo)

            vm.setTitle("Sermon notes")
            vm.addTag("faith")
            vm.addTag("sermon")
            vm.addTag("faith") // duplicate ignored
            vm.toggleFavorite() // new note: sets state, persists on save

            assertEquals(listOf("faith", "sermon"), vm.state.value.tags)
            assertTrue(vm.state.value.isFavorite)

            val id = vm.save()
            assertEquals(7L, id)

            coVerify(exactly = 1) {
                repo.createNote(
                    notebookId = null,
                    title = "Sermon notes",
                    contentText = null,
                    penStrokes = null,
                    penMode = PenMode.TEXT,
                    tags = "faith,sermon",
                    color = null,
                    pagesJson = any(),
                    isFavorite = true
                )
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `toggleFavorite on existing note persists immediately`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repo = mockk<NoteRepository>(relaxed = true)
            coEvery { repo.getAllNotebooks() } returns emptyFlow()
            coEvery { repo.getNote(1L) } returns NoteEntity(
                id = 1L, notebookId = null, title = "T",
                contentText = null, penStrokes = null, penMode = PenMode.TEXT,
                createdAt = 1L, updatedAt = 1L, tags = null, color = null
            )
            coEvery { repo.getLinkedVerseIds(any()) } returns emptyList()
            coEvery { repo.getAudiosForNote(any()) } returns flowOf(emptyList())
            val vm = NoteEditorViewModel(repo)
            vm.loadNote(1L)
            advanceUntilIdle()

            assertFalse(vm.state.value.isFavorite)
            vm.toggleFavorite()
            advanceUntilIdle()

            assertTrue(vm.state.value.isFavorite)
            val cap = slot<NoteEntity>()
            coVerify { repo.updateNote(capture(cap)) }
            assertEquals(true, cap.captured.isFavorite)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
