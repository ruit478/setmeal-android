package com.weekmenu.ui.override

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekmenu.data.db.dao.RecipeDao
import com.weekmenu.data.db.dao.WeeklyPlanDao
import com.weekmenu.data.db.entity.RecipeEntity
import com.weekmenu.data.db.entity.SlotEntity
import com.weekmenu.data.db.entity.WeeklyPlanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
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
        initWeekStart()
    }

    /** Called from the screen to override the initial week start (from nav arg). */
    fun setWeekStart(weekStartStr: String) {
        try {
            val parsed = LocalDate.parse(weekStartStr)
            _uiState.update {
                it.copy(weekStart = parsed, claims = emptyList(), workDays = emptySet())
            }
            checkExistingPlan(parsed)
        } catch (_: Exception) {
            // fallback to current week if parsing fails
            initWeekStart()
        }
    }

    private fun initWeekStart() {
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

    // ── Week navigation ──────────────────────────────────────────

    fun nextWeek() {
        val newWeekStart = _uiState.value.weekStart.plusWeeks(1)
        _uiState.update { it.copy(weekStart = newWeekStart, claims = emptyList(), workDays = emptySet()) }
        checkExistingPlan(newWeekStart)
    }

    fun previousWeek() {
        val newWeekStart = _uiState.value.weekStart.minusWeeks(1)
        _uiState.update { it.copy(weekStart = newWeekStart, claims = emptyList(), workDays = emptySet()) }
        checkExistingPlan(newWeekStart)
    }

    // ── Existing plan loading ──────────────────────────────────────

    private fun checkExistingPlan(weekStart: LocalDate) {
        viewModelScope.launch {
            val existingPlan = weeklyPlanDao.getPlanByWeekStart(weekStart.toString()).first()
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

                // Find or create plan
                var plan = weeklyPlanDao.getPlanByWeekStart(weekStartStr).first()
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

                // Fetch recipes for matching and auto-fill
                val recipes = recipeDao.getRecipesByLeastRecentlyUsed()
                val usedRecipeIds = mutableSetOf<String>()
                val allRecipes = recipes.associateBy { it.name.lowercase() }
                var recipeIdx = 0

                val slotEntities = mutableListOf<SlotEntity>()
                var sortOrder = 0
                var nextBatchGroup = 1

                // Separate claims: batch (multi-portion) vs single
                val batchClaims = state.claims.filter {
                    it.portionCount > 1 && !it.recipeName.isNullOrBlank()
                }
                val singleClaims = state.claims.filter {
                    it.portionCount == 1 && !it.recipeName.isNullOrBlank()
                }

                // Collect all empty slot positions
                val emptySlots = allSlots.toMutableList()

                // 1. Place batch claims in adjacent slots
                for (claim in batchClaims) {
                    if (emptySlots.size < claim.portionCount) break

                    // Find contiguous slots for this batch
                    val batchSlots = findContiguousEmptySlots(emptySlots, claim.portionCount)
                    if (batchSlots == null) break

                    val matchedRecipeId = allRecipes[claim.recipeName!!.lowercase()]?.id
                    if (matchedRecipeId != null) usedRecipeIds.add(matchedRecipeId)

                    for ((i, (day, mealTime)) in batchSlots.withIndex()) {
                        emptySlots.remove(Pair(day, mealTime))
                        slotEntities.add(
                            SlotEntity(
                                planId = planId,
                                dayOfWeek = day,
                                mealTime = mealTime,
                                slotType = if (i == 0) "claimed" else "leftover",
                                recipeId = matchedRecipeId,
                                recipeName = claim.recipeName,
                                batchGroup = nextBatchGroup,
                                batchTotal = claim.portionCount,
                                sortOrder = sortOrder++
                            )
                        )
                    }
                    nextBatchGroup++
                }

                // 2. Place single-portion claims
                for (claim in singleClaims) {
                    if (emptySlots.isEmpty()) break
                    val (day, mealTime) = emptySlots.removeAt(0)

                    val matchedRecipeId = allRecipes[claim.recipeName!!.lowercase()]?.id
                    if (matchedRecipeId != null) usedRecipeIds.add(matchedRecipeId)

                    slotEntities.add(
                        SlotEntity(
                            planId = planId,
                            dayOfWeek = day,
                            mealTime = mealTime,
                            slotType = "claimed",
                            recipeId = matchedRecipeId,
                            recipeName = claim.recipeName,
                            batchGroup = null,
                            batchTotal = null,
                            sortOrder = sortOrder++
                        )
                    )
                }

                // 3. Auto-fill remaining gaps
                for ((day, mealTime) in emptySlots) {
                    if (recipeIdx >= recipes.size) break
                    val recipe = recipes[recipeIdx++]
                    usedRecipeIds.add(recipe.id)

                    slotEntities.add(
                        SlotEntity(
                            planId = planId,
                            dayOfWeek = day,
                            mealTime = mealTime,
                            slotType = "auto_fill",
                            recipeId = recipe.id,
                            recipeName = recipe.name,
                            batchGroup = null,
                            batchTotal = null,
                            sortOrder = sortOrder++
                        )
                    )
                }

                weeklyPlanDao.insertSlots(slotEntities)

                // Update lastUsedDate and cookCount for recipes used
                for (recipeId in usedRecipeIds) {
                    val recipe = recipeDao.getRecipeById(recipeId) ?: continue
                    recipeDao.updateRecipe(recipe.copy(
                        lastUsedDate = weekStartStr,
                        cookCount = recipe.cookCount + 1
                    ))
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

    /**
     * Find N contiguous empty slots in the list.
     * Returns the first contiguous run of [count] slots, or null if none found.
     */
    private fun findContiguousEmptySlots(
        emptySlots: List<Pair<Int, String>>,
        count: Int
    ): List<Pair<Int, String>>? {
        if (emptySlots.size < count) return null

        // Slots are already sorted (dayOfWeek, mealTime). Find a run of [count] consecutive slots.
        for (i in 0..emptySlots.size - count) {
            val run = emptySlots.subList(i, i + count)
            if (isContiguous(run)) {
                return run
            }
        }
        return null
    }

    private fun isContiguous(slots: List<Pair<Int, String>>): Boolean {
        for (i in 1 until slots.size) {
            val prev = slots[i - 1]
            val curr = slots[i]
            // Same day: lunch→dinner is contiguous
            if (prev.first == curr.first) {
                if (prev.second != "lunch" || curr.second != "dinner") return false
            } else {
                // Next day: must be prev+1 and prev=dinner, curr=lunch
                if (curr.first != prev.first + 1) return false
                if (prev.second != "dinner" || curr.second != "lunch") return false
            }
        }
        return true
    }
}
