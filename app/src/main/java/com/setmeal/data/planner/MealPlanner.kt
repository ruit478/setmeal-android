package com.setmeal.data.planner

import com.setmeal.data.db.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

object MealPlanner {

    /**
     * Auto-fill empty slots with recipes in rotation order (oldest unused first).
     * @param emptySlots List of (dayOfWeek, mealTime) pairs to fill
     * @param recipes All available recipes, sorted by usage (oldest first)
     * @param planWeekStart The ISO week number for tracking recipe usage
     * @return List of (dayOfWeek, mealTime, recipeId, recipeName, slotType)
     */
    fun autoFillSlots(
        emptySlots: List<Pair<Int, String>>,
        recipes: List<RecipeEntity>,
        planWeekStart: LocalDate
    ): List<SlotFillResult> {
        // Sort recipes: null lastUsedWeek first, then oldest week first
        val sorted = recipes.sortedBy { it.lastUsedWeek ?: Int.MAX_VALUE }
        val used = mutableSetOf<String>()
        val results = mutableListOf<SlotFillResult>()
        var recipeIndex = 0

        for ((dayOfWeek, mealTime) in emptySlots) {
            // Find next unused recipe, avoiding same-as-previous-adjacent
            var candidate = sorted[recipeIndex % sorted.size]
            var attempts = 0
            while (attempts < sorted.size && (
                used.contains(candidate.id) ||
                (results.isNotEmpty() &&
                 results.last().recipeId == candidate.id &&
                 results.last().dayOfWeek >= dayOfWeek - 1
                )
            )) {
                attempts++
                recipeIndex++
                candidate = sorted[(recipeIndex) % sorted.size]
            }

            used.add(candidate.id)
            results.add(
                SlotFillResult(
                    dayOfWeek = dayOfWeek,
                    mealTime = mealTime,
                    recipeId = candidate.id,
                    recipeName = candidate.name,
                    slotType = "auto_fill",
                    batchGroup = null,
                    batchTotal = null
                )
            )
            recipeIndex = (recipeIndex + 1) % sorted.size
        }

        return results
    }

    /**
     * Group claimed recipes into batch cooking groups.
     * @param claims List of (recipeId, recipeName, dayOfWeek, mealTime, portionCount)
     * @return List of cluster claims with batchGroup/total set
     */
    fun groupBatchClaims(
        claims: List<ClaimInput>
    ): List<BatchClaimResult> {
        val results = mutableListOf<BatchClaimResult>()
        var nextBatchGroup = 1

        for (claim in claims) {
            if (claim.portionCount > 1) {
                // First portion = cooked, rest = leftover
                val adjacentSlots = findAdjacentSlots(claim.dayOfWeek, claim.mealTime, claim.portionCount)
                for ((i, slot) in adjacentSlots.withIndex()) {
                    results.add(
                        BatchClaimResult(
                            dayOfWeek = slot.first,
                            mealTime = slot.second,
                            recipeId = claim.recipeId,
                            recipeName = claim.recipeName,
                            slotType = if (i == 0) "claimed" else "leftover",
                            batchGroup = nextBatchGroup,
                            batchTotal = claim.portionCount
                        )
                    )
                }
                nextBatchGroup++
            } else {
                results.add(
                    BatchClaimResult(
                        dayOfWeek = claim.dayOfWeek,
                        mealTime = claim.mealTime,
                        recipeId = claim.recipeId,
                        recipeName = claim.recipeName,
                        slotType = "claimed",
                        batchGroup = null,
                        batchTotal = null
                    )
                )
            }
        }

        return results
    }

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
            // Move to next slot (dinner -> next day lunch, lunch -> dinner)
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

data class SlotFillResult(
    val dayOfWeek: Int,
    val mealTime: String,
    val recipeId: String?,
    val recipeName: String?,
    val slotType: String,
    val batchGroup: Int?,
    val batchTotal: Int?
)

data class ClaimInput(
    val recipeId: String,
    val recipeName: String,
    val dayOfWeek: Int,
    val mealTime: String,
    val portionCount: Int
)

data class BatchClaimResult(
    val dayOfWeek: Int,
    val mealTime: String,
    val recipeId: String?,
    val recipeName: String?,
    val slotType: String,
    val batchGroup: Int?,
    val batchTotal: Int?
)
