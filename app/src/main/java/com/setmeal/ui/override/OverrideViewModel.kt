package com.setmeal.ui.override

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.RecipeDao
import com.setmeal.data.db.dao.WeeklyPlanDao
import com.setmeal.data.db.entity.RecipeEntity
import com.setmeal.data.db.entity.SlotEntity
import com.setmeal.data.db.entity.WeeklyPlanEntity
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
    val recipeName: String? = null,
    val portionCount: Int = 1
)

data class OverrideUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val workDays: Set<Int> = emptySet(),
    val claims: List<Claim> = emptyList(),
    val recipes: List<RecipeEntity> = emptyList(),
    val isSaving: Boolean = false,
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
                        recipeName = primary.recipeName,
                        portionCount = primary.batchTotal ?: 1
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
                        claim.copy(recipeName = dishName)
                    else claim
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

    // ── Save (distributes claims + auto-fills gaps) ──────────────────

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

                // Build all week slots (ordered: Mon lunch, Mon dinner, Tue lunch, …)
                val allSlots = buildAllSlots(state.workDays)

                // Expand user claims into a single portion pool
                val portionPool = mutableListOf<Pair<String, Int>>()  // (recipeName, claimIndex)

                for ((idx, claim) in state.claims.withIndex()) {
                    if (claim.recipeName.isNullOrBlank()) continue
                    repeat(claim.portionCount) {
                        portionPool.add(Pair(claim.recipeName, idx))
                    }
                }

                // Shuffle for random distribution across the week
                portionPool.shuffle()

                // Fetch recipes for auto-fill gaps
                val recipes = recipeDao.getRecipesByLeastRecentlyUsed()
                var recipeIdx = 0
                val usedRecipeIds = mutableSetOf<String>()

                val slotEntities = mutableListOf<SlotEntity>()
                var sortOrder = 0

                for ((day, mealTime) in allSlots) {
                    if (portionPool.isNotEmpty()) {
                        // Place a user-claimed portion
                        val (name, claimIdx) = portionPool.removeAt(0)
                        val claimPortions = state.claims[claimIdx].portionCount
                        val batchGroup: Int? = if (claimPortions > 1) (claimIdx + 1) else null
                        val batchTotal: Int? = if (claimPortions > 1) claimPortions else null

                        slotEntities.add(
                            SlotEntity(
                                planId = planId,
                                dayOfWeek = day,
                                mealTime = mealTime,
                                slotType = "claimed",
                                recipeId = null,
                                recipeName = name,
                                batchGroup = batchGroup,
                                batchTotal = batchTotal,
                                sortOrder = sortOrder++
                            )
                        )
                    } else if (recipeIdx < recipes.size) {
                        // Auto-fill with recipe from bank
                        val recipe = recipes[recipeIdx++]
                        usedRecipeIds.add(recipe.id)

                        slotEntities.add(
                            SlotEntity(
                                planId = planId,
                                dayOfWeek = day,
                                mealTime = mealTime,
                                slotType = "claimed",
                                recipeId = recipe.id,
                                recipeName = recipe.name,
                                batchGroup = null,
                                batchTotal = null,
                                sortOrder = sortOrder++
                            )
                        )
                    } else {
                        // No claims left and no recipes — leave as gap
                        break
                    }
                }

                weeklyPlanDao.insertSlots(slotEntities)

                // Update lastUsedWeek for auto-filled recipes
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
}
