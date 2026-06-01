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

    fun updateClaimDayOfWeek(claimId: String, dayOfWeek: Int) {
        _uiState.update { state ->
            state.copy(
                claims = state.claims.map { claim ->
                    if (claim.id == claimId) claim.copy(dayOfWeek = dayOfWeek) else claim
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
                // Compute all available slots for the week
                val allSlots = mutableListOf<Pair<Int, String>>()
                for (day in 0..6) {
                    if (day in state.workDays) {
                        allSlots.add(Pair(day, "dinner"))
                    } else {
                        allSlots.add(Pair(day, "lunch"))
                        allSlots.add(Pair(day, "dinner"))
                    }
                }

                // Slots already claimed by the user
                val claimedSlots = state.claims
                    .map { Pair(it.dayOfWeek, it.mealTime) }
                    .toSet()

                // Empty slots = all possible minus claimed
                val emptySlots = allSlots.filter { it !in claimedSlots }

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

                // Build slot entities from claims
                val slotEntities = mutableListOf<SlotEntity>()
                // Track batch groups for multi-portion claims
                var nextBatchGroup = 1

                for ((batchIndex, claim) in state.claims.withIndex()) {
                    if (claim.portionCount > 1) {
                        // Multi-portion: first = "claimed", rest = "leftover"
                        val adjacentSlots = findAdjacentSlots(
                            claim.dayOfWeek, claim.mealTime, claim.portionCount
                        )
                        for ((i, slot) in adjacentSlots.withIndex()) {
                            slotEntities.add(
                                SlotEntity(
                                    planId = planId,
                                    dayOfWeek = slot.first,
                                    mealTime = slot.second,
                                    slotType = if (i == 0) "claimed" else "leftover",
                                    recipeId = claim.recipeId,
                                    recipeName = claim.recipeName,
                                    batchGroup = nextBatchGroup,
                                    batchTotal = claim.portionCount,
                                    sortOrder = slotEntities.size
                                )
                            )
                        }
                        nextBatchGroup++
                    } else {
                        slotEntities.add(
                            SlotEntity(
                                planId = planId,
                                dayOfWeek = claim.dayOfWeek,
                                mealTime = claim.mealTime,
                                slotType = "claimed",
                                recipeId = claim.recipeId,
                                recipeName = claim.recipeName,
                                batchGroup = null,
                                batchTotal = null,
                                sortOrder = slotEntities.size
                            )
                        )
                    }
                }

                weeklyPlanDao.insertSlots(slotEntities)

                // Update lastUsedWeek for all used recipes
                val usedRecipeIds = state.claims
                    .map { it.recipeId }
                    .filterNotNull()
                    .distinct()

                for (recipeId in usedRecipeIds) {
                    val recipe = recipeDao.getRecipeById(recipeId) ?: continue
                    if (recipe.lastUsedWeek == null || recipe.lastUsedWeek != isoWeek) {
                        recipeDao.updateRecipe(recipe.copy(lastUsedWeek = isoWeek))
                    }
                }

                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = "Save error: ${e.message}")
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun findAdjacentSlots(
        startDay: Int,
        startMeal: String,
        count: Int
    ): List<Pair<Int, String>> {
        val slots = mutableListOf<Pair<Int, String>>()
        var day = startDay
        var meal = startMeal
        for (i in 0 until count) {
            slots.add(Pair(day, meal))
            if (meal == "lunch") {
                meal = "dinner"
            } else {
                day = (day + 1) % 7
                meal = "lunch"
            }
        }
        return slots
    }
}
