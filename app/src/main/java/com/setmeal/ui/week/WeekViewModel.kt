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
    val dayOfWeek: Int,          // 0=Mon..6=Sun
    val mealTime: String,        // "lunch" or "dinner"
    val recipeId: String?,
    val recipeName: String?,
    val slotType: String?        // "claimed", "auto_fill", "work", or null for empty
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

    val weekGrid: StateFlow<List<WeekSummary>> = allSlots
        .map { slots ->
            val slotMap = slots.associateBy { "${it.dayOfWeek}_${it.mealTime}" }
            val meals = listOf("lunch", "dinner")
            buildList {
                for (meal in meals) {
                    for (day in 0..6) {
                        val key = "${day}_${meal}"
                        val slot = slotMap[key]
                        add(
                            WeekSummary(
                                dayOfWeek = day,
                                mealTime = meal,
                                recipeId = slot?.recipeId,
                                recipeName = slot?.recipeName,
                                slotType = slot?.slotType
                            )
                        )
                    }
                }
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
