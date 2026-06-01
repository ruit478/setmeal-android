package com.setmeal.data.db.dao

import androidx.room.*
import com.setmeal.data.db.entity.SlotEntity
import com.setmeal.data.db.entity.WeeklyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyPlanDao {
    @Query("SELECT * FROM weekly_plans ORDER BY weekStart DESC")
    fun getAllPlans(): Flow<List<WeeklyPlanEntity>>

    @Query("SELECT * FROM weekly_plans ORDER BY weekStart DESC LIMIT 1")
    fun getCurrentPlan(): Flow<WeeklyPlanEntity?>

    @Query("SELECT * FROM weekly_plans WHERE weekStart = :weekStart LIMIT 1")
    fun getPlanByWeekStart(weekStart: String): Flow<WeeklyPlanEntity?>

    @Query("SELECT * FROM slots WHERE planId = :planId ORDER BY dayOfWeek ASC, sortOrder ASC")
    fun getSlotsForPlan(planId: String): Flow<List<SlotEntity>>

    @Query("SELECT * FROM slots WHERE planId = :planId ORDER BY dayOfWeek ASC, sortOrder ASC")
    suspend fun getSlotsForPlanList(planId: String): List<SlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WeeklyPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<SlotEntity>)

    @Update
    suspend fun updateSlot(slot: SlotEntity)

    @Delete
    suspend fun deletePlan(plan: WeeklyPlanEntity)

    @Query("DELETE FROM slots WHERE planId = :planId")
    suspend fun deleteSlotsForPlan(planId: String)

    @Transaction
    suspend fun insertPlanWithSlots(plan: WeeklyPlanEntity, slots: List<SlotEntity>) {
        insertPlan(plan)
        insertSlots(slots)
    }

    @Transaction
    suspend fun deletePlanWithSlots(plan: WeeklyPlanEntity) {
        deleteSlotsForPlan(plan.id)
        deletePlan(plan)
    }
}
