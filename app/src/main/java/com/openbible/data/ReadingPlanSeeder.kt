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
    /** Name used by the old prepop pipeline ("1-Year Bible", 321 days, not 365). */
    private const val LEGACY_PLAN_NAME = "1-Year Bible"
    private const val PLAN_DESCRIPTION = "Read through the entire Bible in 365 days"
    private const val PLAN_DURATION = 365

    /**
     * Seed if no healthy plan exists. Safe to call repeatedly.
     *
     * Only a plan WE own (by name) is ever rebuilt, and only when it has no
     * progress rows — other plans (user-created, legacy prepop) are never
     * touched. A half-seeded own plan from a crashed first launch is wiped
     * and rebuilt (days/progress CASCADE off the plan row).
     */
    suspend fun ensureSeeded(dao: ReadingPlanDao, bibleDao: BibleDao) {
        // Check if already seeded — match by name, never by position.
        val existing = dao.getAllPlansOnce()
        val ours = existing.firstOrNull { it.name == PLAN_NAME || it.name == LEGACY_PLAN_NAME }
        if (ours != null) {
            if (dao.getPlanDayCount(ours.id) >= PLAN_DURATION) return
            // Partial own/legacy plan: rebuild only if nobody started it.
            if (dao.getProgressOnce(ours.id).isNotEmpty()) return
        }

        // Get chapter counts per book (canonical order)
        val bookCounts = bibleDao.getBookChapterCounts("kjv")

        // Build flat list of (bookId, chapter) pairs
        val allChapters = mutableListOf<Pair<Int, Int>>()
        for ((bookId, chapterCount) in bookCounts) {
            for (chapter in 1..chapterCount) {
                allChapters.add(Pair(bookId, chapter))
            }
        }

        if (allChapters.isEmpty()) return  // no data yet — retry next launch, delete nothing

        // Data is good: now it is safe to drop the partial own plan.
        if (ours != null) dao.deletePlanById(ours.id)

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
        // Days are inserted in one transaction — no half plans on crash.
        val base = allChapters.size / PLAN_DURATION
        val extra = allChapters.size % PLAN_DURATION
        var chapterIndex = 0
        val days = ArrayList<ReadingPlanDayEntity>(PLAN_DURATION)
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

            days.add(
                ReadingPlanDayEntity(
                    planId = planId,
                    dayNumber = day,
                    title = "Day $day",
                    readings = readingsJson
                )
            )
        }
        dao.insertDays(days)
    }
}
