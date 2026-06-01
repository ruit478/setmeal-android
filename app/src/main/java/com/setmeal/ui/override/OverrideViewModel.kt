package com.setmeal.ui.override

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.RecipeDao
import com.setmeal.data.db.dao.WeeklyPlanDao
import com.setmeal.data.db.entity.RecipeEntity
import com.setmeal.data.db.entity.SlotEntity
import com.setmeal.data.db.entity.WeeklyPlanEntity
import com.setmeal.data.planner.MealPlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.UUID
import javax.inject.Inject

data class Claim(
    val id: String = UUID.randomUUID().toString(),
    val recipeId: String? = null,
    val recipeName: String? = null,
    val portionCount: Int = 1,
    val dayOfWeek: Int = 0,
    val mealTime: String = "lunch"
)

data class OverrideUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val workDays: Set<Int> = emptySet(),
    val claims: List<Claim> = emptyList(),
    val recipes: List<RecipeEntity> = emptyList(),
    val isSaving: Boolean = false,
    val isAutoFilling: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OverrideViewModel @Inject constructor(
    private val recipeDao: RecipeDao,
    private val weeklyPlanDao: WeeklyPlanDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverrideUiState())
    val uiState: StateFlow<OverrideUiState> = _uiState.asStateFlow()

    init {
        val weekStart = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _uiState.update { it.copy(weekStart = weekStart) }

        viewModelScope.launch {
            recipeDao.getAllRecipes().collect { recipes ->
                _uiState.update { it.copy(recipes = recipes) }
            }
        }

        checkExistingPlan(weekStart)
    }

    // ── Existing plan loading ──────────────────────────────────────

    private fun checkExistingPlan(weekStart: LocalDate) {
        viewModelScope.launch {
            val existingPlan = weeklyPlanDao.getPlanByWeekStart(weekStart.toString())
            if (existingPlan != null) {
                val slots = weeklyPlanDao.getSlotsForPlanList(existingPlan.id)

                // Group slots by batchGroup to reconstruct multi-portion claims
                val slotGroups = slots.groupBy { it.batchGroup ?: UUID.randomUUID().toString() }
                val loadedClaims = slotGroups.map { (_, group) ->
                    val primary = group.minByOrNull { it.sortOrder } ?: group.first()
                    Claim(
                        recipeId = primary.recipeId,
                        recipeName = primary.recipeName,
                        portionCount = primary.batchTotal ?: 1,
                        dayOfWeek = primary.dayOfWeek,
                        mealTime = primary.mealTime
                    )
                }

                // Derive work days: any day that has slots but no lunch slot is a work day
                val daysWithSlots = slots.map { it.dayOfWeek }.distinct()
                val loadedWorkDays = (0..6).filter { day ->
                    day in daysWithSlots &&
                        slots.none { it.dayOfWeek == day && it.mealTime == "lunch" }
                }.toSet()

                _uiState.update {
                    it.copy(claims = loadedClaims, workDays = loadedWorkDays)
                }
            }
        }
    }

    // ── Work days ──────────────────────────────────────────────────

    fun toggleWorkDay(day: Int) {
        _uiState.update { state ->
            val newWorkDays = if (day in state.workDays)
                state.workDays - day
            else
                state.workDays + day
            state.copy(workDays = newWorkDays)
        }
    }

    // ── Claims ─────────────────────────────────────────────────────

    fun addClaim() {
        _uiState.update { state ->
            state.copy(claims = state.claims + Claim())
        }
    }

    fun removeClaim(claimId: String) {
        _uiState.update { state ->
            state.copy(claims = state.claims.filter { it.id != claimId })
        }
    }

    fun updateClaimDishName(claimId: String, dishName: String) {
        _uiState.update { state ->
            state.copy(
                claims = state.claims.map { claim ->
                    if (claim.id == claimId)
                        claim.copy(recipeId = null, recipeName = dishName)
                    else claim
                }
            )
        }
    }

    fun updateClaimMealTime(claimId: String, mealTime: String) {
        _uiState.update { state ->
            state.copy(
                claims = state.claims.map { claim ->
                    if (claim.id == claimId) claim.copy(mealTime = mealTime) else claim
                }
            )
        }
    }

    fun updateClaimPortionCount(claimId: String, portionCount: Int) {
        _uiState.update { state ->
            val clamped = portionCount.coerceAtLeast(1)
            state.copy(
                claims = state.claims.map { claim ->
                    if (claim.id == claimId) claim.copy(portionCount = clamped) else claim
                }
            )
        }
    }

    // ── Auto-fill ──────────────────────────────────────────────────

    fun autoFillRemaining() {
        val state = _uiState.value
        _uiState.update { it.copy(isAutoFilling = true) }

        viewModelScope.launch {
            try {
                // Compute all possible slots for the week
                val allSlots = buildAllSlots(state.workDays)
                val usedSlots = mutableSetOf<Pair<Int, String>>()

                // Simulate allocation of existing claims (in order) to find empty slots
                for (claim in state.claims) {
                    allocateSlots(allSlots, usedSlots, claim.mealTime, claim.portionCount)
                }

                // Remaining slots = all minus those taken by user claims
                val emptySlots = allSlots.filter { it !in usedSlots }

                if (emptySlots.isEmpty()) {
                    _uiState.update { it.copy(isAutoFilling = false) }
                    return@launch
                }

                val recipes = recipeDao.getRecipesByLeastRecentlyUsed()

                val fillResults = MealPlanner.autoFillSlots(
                    emptySlots = emptySlots,
                    recipes = recipes,
                    planWeekStart = state.weekStart
                )

                val newClaims = fillResults.map { result ->
                    Claim(
                        recipeId = result.recipeId,
                        recipeName = result.recipeName,
                        portionCount = result.batchTotal ?: 1,
                        dayOfWeek = result.dayOfWeek,
                        mealTime = result.mealTime
                    )
                }

                _uiState.update {
                    it.copy(claims = it.claims + newClaims, isAutoFilling = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isAutoFilling = false, error = "Auto-fill error: ${e.message}")
                }
            }
        }
    }

    // ── Save ───────────────────────────────────────────────────────

    fun save() {
        val state = _uiState.value

        val invalidClaims = state.claims.filter {
            it.recipeName.isNullOrBlank()
        }
        if (invalidClaims.isNotEmpty()) {
            _uiState.update {
                it.copy(error = "All meals need a dish selected")
            }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            try {
                val weekStartStr = state.weekStart.toString()
                val isoWeek = state.weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())

                // Find or create plan
                var plan = weeklyPlanDao.getPlanByWeekStart(weekStartStr)
                val planId: String

                if (plan != null) {
                    planId = plan.id
                    weeklyPlanDao.deleteSlotsForPlan(planId)
                } else {
                    plan = WeeklyPlanEntity(
                        id = UUID.randomUUID().toString(),
                        weekStart = weekStartStr
                    )
                    weeklyPlanDao.insertPlan(plan)
                    planId = plan.id
                }

                // Build all week slots and auto-assign days to claims
                val allSlots = buildAllSlots(state.workDays)
                val usedSlots = mutableSetOf<Pair<Int, String>>()

                val slotEntities = mutableListOf<SlotEntity>()
                var nextBatchGroup = 1

                for (claim in state.claims) {
                    if (claim.recipeName.isNullOrBlank()) continue

                    val claimSlots = allocateSlots(allSlots, usedSlots, claim.mealTime, claim.portionCount)

                    if (claimSlots.isEmpty()) continue // No space left for this claim

                    if (claim.portionCount > 1) {
                        // Multi-portion: first = "claimed", rest = "leftover"
                        for ((i, slot) in claimSlots.withIndex()) {
                            slotEntities.add(
                                SlotEntity(
                                    planId = planId,
                                    dayOfWeek = slot.first,
                                    mealTime = slot.second,
                                    slotType = if (i == 0) "claimed" else "leftover",
                                    recipeId = null,
                                    recipeName = claim.recipeName,
                                    batchGroup = nextBatchGroup,
                                    batchTotal = claim.portionCount,
                                    sortOrder = slotEntities.size
                                )
                            )
                        }
                        nextBatchGroup++
                    } else {
                        val slot = claimSlots.first()
                        slotEntities.add(
                            SlotEntity(
                                planId = planId,
                                dayOfWeek = slot.first,
                                mealTime = slot.second,
                                slotType = "claimed",
                                recipeId = null,
                                recipeName = claim.recipeName,
                                batchGroup = null,
                                batchTotal = null,
                                sortOrder = slotEntities.size
                            )
                        )
                    }
                }

                weeklyPlanDao.insertSlots(slotEntities)

                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Save error: ${e.message}")
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun buildAllSlots(workDays: Set<Int>): List<Pair<Int, String>> {
        val slots = mutableListOf<Pair<Int, String>>()
        for (day in 0..6) {
            if (day in workDays) {
                slots.add(Pair(day, "dinner"))
            } else {
                slots.add(Pair(day, "lunch"))
                slots.add(Pair(day, "dinner"))
            }
        }
        return slots
    }

    private fun allocateSlots(
        allSlots: List<Pair<Int, String>>,
        usedSlots: MutableSet<Pair<Int, String>>,
        mealTime: String,
        count: Int
    ): List<Pair<Int, String>> {
        val startIdx = allSlots.indexOfFirst { it.second == mealTime && it !in usedSlots }
        if (startIdx < 0) return emptyList()

        val result = mutableListOf<Pair<Int, String>>()
        var idx = startIdx
        while (result.size < count && idx < allSlots.size) {
            val slot = allSlots[idx]
            if (slot !in usedSlots) {
                result.add(slot)
                usedSlots.add(slot)
            }
            idx++
        }
        return result
    }
}
