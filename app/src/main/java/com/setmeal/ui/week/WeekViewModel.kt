package com.setmeal.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.WeeklyPlanDao
import com.setmeal.data.db.entity.WeeklyPlanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val weeklyPlanDao: WeeklyPlanDao
) : ViewModel() {

    private val _currentWeekStart = MutableStateFlow(getCurrentWeekStart())
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart.asStateFlow()

    val currentPlan: StateFlow<WeeklyPlanEntity?> = _currentWeekStart
        .flatMapLatest { weekStart ->
            weeklyPlanDao.getPlanByWeekStart(weekStart.toString())
                .map { it }
                .flowOn(kotlinx.coroutines.Dispatchers.IO)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasPlan: StateFlow<Boolean> = currentPlan
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday
    }
}
