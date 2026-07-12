package com.openbible.data

import com.openbible.data.db.dao.BibleDao
import com.openbible.data.db.dao.ReadingPlanDao
import com.openbible.data.db.entity.ReadingPlanDayEntity
import com.openbible.data.db.entity.ReadingPlanEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Seeds the "Bible in a Year" reading plan on first launch.
 *
 * Divides all 1,189 chapters of the KJV Bible across 365 days,
 * ~3–4 chapters per day. Uses built-in org.json (Android SDK) —
 * no extra dependencies.
 */
object ReadingPlanSeeder {

    private const val PLAN_NAME = "Bible in a Year"
    private const val PLAN_DESCRIPTION = "Read through the entire Bible in 365 days"
    private const val PLAN_DURATION = 365

    /**
     * Seed if no plans exist. Safe to call repeatedly — checks first.
     */
    suspend fun ensureSeeded(dao: ReadingPlanDao, bibleDao: BibleDao) {
        // Check if already seeded
        val existing = dao.getAllPlansOnce()
        if (existing.isNotEmpty()) return

        // Get chapter counts per book (canonical order)
        val bookCounts = bibleDao.getBookChapterCounts("kjv")

        // Build flat list of (bookId, chapter) pairs
        val allChapters = mutableListOf<Pair<Int, Int>>()
        for ((bookId, chapterCount) in bookCounts) {
            for (chapter in 1..chapterCount) {
                allChapters.add(Pair(bookId, chapter))
            }
        }

        if (allChapters.isEmpty()) return  // no data yet

        // Create the plan
        val planId = dao.insertPlan(
            ReadingPlanEntity(
                name = PLAN_NAME,
                description = PLAN_DESCRIPTION,
                durationDays = PLAN_DURATION,
                isPrebuilt = true
            )
        )

        // Distribute every chapter evenly across PLAN_DURATION days.
        // First `extra` days get one extra chapter so all chapters are covered.
        val base = allChapters.size / PLAN_DURATION
        val extra = allChapters.size % PLAN_DURATION
        var chapterIndex = 0
        for (day in 1..PLAN_DURATION) {
            val count = base + if (day <= extra) 1 else 0

            val dayChapters = mutableListOf<Pair<Int, Int>>()
            repeat(count) {
                if (chapterIndex < allChapters.size) {
                    dayChapters.add(allChapters[chapterIndex])
                    chapterIndex++
                }
            }

            // Build JSON readings array
            val readingsJson = JSONArray().apply {
                dayChapters.forEach { (bookId, chapter) ->
                    put(JSONObject().apply {
                        put("bookId", bookId)
                        put("chapter", chapter)
                    })
                }
            }.toString()

            dao.insertDay(
                ReadingPlanDayEntity(
                    planId = planId,
                    dayNumber = day,
                    title = "Day $day",
                    readings = readingsJson
                )
            )
        }
    }
}
