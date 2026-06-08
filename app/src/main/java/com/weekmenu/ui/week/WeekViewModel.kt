package com.weekmenu.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekmenu.data.db.dao.WeeklyPlanDao
import com.weekmenu.data.db.entity.SlotEntity
import com.weekmenu.data.db.entity.WeeklyPlanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekSummary(
    val dayOfWeek: Int,
    val slotId: String?,
    val recipeId: String?,
    val recipeName: String?,
    val slotType: String?,
    val batchGroup: Int? = null,
    val batchTotal: Int? = null
)

data class DayMeals(
    val dayOfWeek: Int,
    val meals: List<WeekSummary>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val weeklyPlanDao: WeeklyPlanDao
) : ViewModel() {

    private val _currentWeekStart = MutableStateFlow(getCurrentWeekStart())
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart.asStateFlow()

    private val currentPlan: Flow<WeeklyPlanEntity?> = _currentWeekStart
        .flatMapLatest { weekStart ->
            weeklyPlanDao.getPlanByWeekStart(weekStart.toString())
        }

    val hasPlan: StateFlow<Boolean> = currentPlan
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val allSlots: StateFlow<List<SlotEntity>> = currentPlan
        .flatMapLatest { plan ->
            if (plan != null) {
                weeklyPlanDao.getSlotsForPlan(plan.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekGrid: StateFlow<List<DayMeals>> = allSlots
        .map { slots ->
            (0..6).map { day ->
                val daySlots = slots.filter { it.dayOfWeek == day }
                    .sortedBy { if (it.mealTime == "lunch") 0 else 1 }

                val mealNames = mutableListOf<WeekSummary>()
                val lunch = daySlots.find { it.mealTime == "lunch" }
                val dinner = daySlots.find { it.mealTime == "dinner" }

                // Detect work day: has dinner but no lunch on that day
                val isWorkDay = dinner != null && lunch == null

                // First slot — lunch or "Work" if work day, or empty
                if (isWorkDay) {
                    mealNames.add(
                        WeekSummary(
                            dayOfWeek = day,
                            slotId = null,
                            recipeId = null,
                            recipeName = "Work",
                            slotType = "work"
                        )
                    )
                } else {
                    mealNames.add(
                        WeekSummary(
                            dayOfWeek = day,
                            slotId = lunch?.id,
                            recipeId = lunch?.recipeId,
                            recipeName = lunch?.recipeName,
                            slotType = lunch?.slotType,
                            batchGroup = lunch?.batchGroup,
                            batchTotal = lunch?.batchTotal
                        )
                    )
                }
                // Second slot — dinner (or empty)
                mealNames.add(
                    WeekSummary(
                        dayOfWeek = day,
                        slotId = dinner?.id,
                        recipeId = dinner?.recipeId,
                        recipeName = dinner?.recipeName,
                        slotType = dinner?.slotType,
                        batchGroup = dinner?.batchGroup,
                        batchTotal = dinner?.batchTotal
                    )
                )
                DayMeals(day, mealNames)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun resetPlan() {
        viewModelScope.launch {
            val plan = currentPlan.first() ?: return@launch
            weeklyPlanDao.deletePlanWithSlots(plan)
        }
    }

    fun previousWeek() {
        _currentWeekStart.value = _currentWeekStart.value.minusWeeks(1)
    }

    fun nextWeek() {
        _currentWeekStart.value = _currentWeekStart.value.plusWeeks(1)
    }

    fun resetToCurrentWeek() {
        _currentWeekStart.value = getCurrentWeekStart()
    }

    /**
     * Move a meal slot to a new (day, mealTime) position.
     * If the target position is occupied, the two slots swap positions.
     * Moving to a work day lunch is rejected (work days have no lunch).
     */
    fun moveMeal(
        slotId: String,
        sourceDay: Int,
        sourceMeal: String,
        targetDay: Int,
        targetMeal: String
    ) {
        viewModelScope.launch {
            val plan = currentPlan.first() ?: return@launch

            // Reject work-day lunch moves
            val existingDinner = weeklyPlanDao.getSlot(plan.id, targetDay, "dinner")
            val existingLunch = weeklyPlanDao.getSlot(plan.id, targetDay, "lunch")
            val isWorkDay = existingDinner != null && existingLunch == null
            if (isWorkDay && targetMeal == "lunch") return@launch

            // Check if target is occupied
            val targetSlot = weeklyPlanDao.getSlot(plan.id, targetDay, targetMeal)
            if (targetSlot != null) {
                // Swap: move target slot to source position
                weeklyPlanDao.updateSlotPosition(targetSlot.id, sourceDay, sourceMeal)
            }
            // Move source slot to target position
            weeklyPlanDao.updateSlotPosition(slotId, targetDay, targetMeal)
        }
    }

    private fun getCurrentWeekStart(): LocalDate {
        val today = LocalDate.now()
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    companion object {
        val WEEK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
}
