package com.setmeal.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.WeeklyPlanDao
import com.setmeal.data.db.entity.SlotEntity
import com.setmeal.data.db.entity.WeeklyPlanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekSummary(
    val dayOfWeek: Int,
    val recipeId: String?,
    val recipeName: String?,
    val slotType: String?
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

    val weekEnd: StateFlow<LocalDate> = _currentWeekStart
        .map { it.plusDays(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _currentWeekStart.value.plusDays(6))

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

                // Enforce exactly 2 display entries per day (fill empty if missing)
                val mealNames = mutableListOf<WeekSummary>()
                val lunch = daySlots.find { it.mealTime == "lunch" }
                val dinner = daySlots.find { it.mealTime == "dinner" }

                // Lunch slot (or empty)
                mealNames.add(
                    WeekSummary(
                        dayOfWeek = day,
                        recipeId = lunch?.recipeId,
                        recipeName = lunch?.recipeName,
                        slotType = lunch?.slotType
                    )
                )
                // Dinner slot (or empty)
                mealNames.add(
                    WeekSummary(
                        dayOfWeek = day,
                        recipeId = dinner?.recipeId,
                        recipeName = dinner?.recipeName,
                        slotType = dinner?.slotType
                    )
                )
                DayMeals(day, mealNames)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun previousWeek() {
        _currentWeekStart.value = _currentWeekStart.value.minusWeeks(1)
    }

    fun nextWeek() {
        _currentWeekStart.value = _currentWeekStart.value.plusWeeks(1)
    }

    fun resetToCurrentWeek() {
        _currentWeekStart.value = getCurrentWeekStart()
    }

    private fun getCurrentWeekStart(): LocalDate {
        val today = LocalDate.now()
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    companion object {
        val WEEK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }
}
