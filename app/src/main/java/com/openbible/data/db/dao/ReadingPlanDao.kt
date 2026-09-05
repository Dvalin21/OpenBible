package com.openbible.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.openbible.data.db.entity.ReadingPlanDayEntity
import com.openbible.data.db.entity.ReadingPlanEntity
import com.openbible.data.db.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingPlanDao {

    // -- Plans --

    @Query("SELECT * FROM reading_plans ORDER BY durationDays")
    fun getAllPlans(): Flow<List<ReadingPlanEntity>>

    @Query("SELECT * FROM reading_plans ORDER BY durationDays")
    suspend fun getAllPlansOnce(): List<ReadingPlanEntity>

    @Query("SELECT * FROM reading_plans WHERE id = :planId")
    suspend fun getPlan(planId: Long): ReadingPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: ReadingPlanEntity): Long

    // -- Plan Days --

    @Query("SELECT * FROM reading_plan_days WHERE planId = :planId ORDER BY dayNumber")
    fun getPlanDays(planId: Long): Flow<List<ReadingPlanDayEntity>>

    @Query("SELECT * FROM reading_plan_days WHERE planId = :planId AND dayNumber = :dayNumber")
    suspend fun getPlanDay(planId: Long, dayNumber: Int): ReadingPlanDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDay(day: ReadingPlanDayEntity): Long

    /** Batch day insert in one transaction — seeding 365 days one-by-one is
     *  slow and a crash mid-loop leaves a half plan. */
    @Transaction
    suspend fun insertDays(days: List<ReadingPlanDayEntity>) {
        days.forEach { insertDay(it) }
    }

    @Query("SELECT COUNT(*) FROM reading_plan_days WHERE planId = :planId")
    suspend fun getPlanDayCount(planId: Long): Int

    @Query("DELETE FROM reading_plans WHERE id = :planId")
    suspend fun deletePlanById(planId: Long)

    // -- Progress --

    @Query("SELECT * FROM reading_progress WHERE planId = :planId ORDER BY dayNumber")
    fun getProgress(planId: Long): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE planId = :planId AND dayNumber = :dayNumber")
    suspend fun getDayProgress(planId: Long, dayNumber: Int): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE planId = :planId")
    suspend fun getProgressOnce(planId: Long): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ReadingProgressEntity): Long

    @Query("UPDATE reading_progress SET completed = :completed, completedAt = :completedAt WHERE planId = :planId AND dayNumber = :dayNumber")
    suspend fun updateProgress(planId: Long, dayNumber: Int, completed: Boolean, completedAt: Long?)
}
