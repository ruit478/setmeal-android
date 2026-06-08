package com.weekmenu.ui.grocery

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekmenu.data.Categories
import com.weekmenu.data.db.dao.RecipeDao
import com.weekmenu.data.db.dao.WeeklyPlanDao
import com.weekmenu.data.db.entity.IngredientEntity
import com.weekmenu.data.db.entity.SlotEntity
import com.weekmenu.data.db.entity.WeeklyPlanEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** An auto-aggregated grocery item derived from plan recipe ingredients. */
data class AutoGroceryItem(
    val name: String,
    val quantity: String?,
    val category: String,
    val sourceRecipeNames: List<String>,
    val key: String = "${name.lowercase()}_${category}"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val weeklyPlanDao: WeeklyPlanDao,
    private val recipeDao: RecipeDao
) : ViewModel() {

    companion object {
        private val PANTRY_STAPLES = setOf(
            "sal", "pimenta", "azeite", "esparguete", "arroz"
        )
        val WEEK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    }

    // ── Week navigation ──

    private val _currentWeekStart = MutableStateFlow(getCurrentWeekStart())
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart.asStateFlow()

    private val currentPlan: Flow<WeeklyPlanEntity?> = _currentWeekStart
        .flatMapLatest { weekStart ->
            weeklyPlanDao.getPlanByWeekStart(weekStart.toString())
        }

    val hasPlan: StateFlow<Boolean> = currentPlan
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Auto items (reactive to week AND slot changes) ──

    val autoItems: StateFlow<List<AutoGroceryItem>> = _currentWeekStart
        .flatMapLatest { weekStart ->
            weeklyPlanDao.getPlanByWeekStart(weekStart.toString())
                .flatMapLatest { plan ->
                    if (plan != null) {
                        weeklyPlanDao.getSlotsForPlan(plan.id)
                            .mapLatest { slots -> computeAutoItems(slots) }
                    } else {
                        flowOf(emptyList())
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _checkedKeys = MutableStateFlow<Set<String>>(emptySet())
    val checkedKeys: StateFlow<Set<String>> = _checkedKeys.asStateFlow()

    fun previousWeek() {
        _currentWeekStart.value = _currentWeekStart.value.minusWeeks(1)
    }

    fun nextWeek() {
        _currentWeekStart.value = _currentWeekStart.value.plusWeeks(1)
    }

    fun resetToCurrentWeek() {
        _currentWeekStart.value = getCurrentWeekStart()
    }

    // ── Check state ──

    fun toggleItem(item: AutoGroceryItem) {
        val current = _checkedKeys.value.toMutableSet()
        if (current.contains(item.key)) current.remove(item.key)
        else current.add(item.key)
        _checkedKeys.value = current
    }

    fun clearChecked() {
        _checkedKeys.value = emptySet()
    }

    // ── Item computation ──

    private suspend fun computeAutoItems(slots: List<SlotEntity>): List<AutoGroceryItem> {
        val recipeIds = slots.mapNotNull { it.recipeId }.distinct()
        val allIngredientSources = mutableListOf<Pair<IngredientEntity, String>>()
        for (recipeId in recipeIds) {
            val recipe = recipeDao.getRecipeById(recipeId)
            val recipeName = recipe?.name ?: ""
            val ingredients = recipeDao.getIngredientsForRecipe(recipeId)
            for (ingredient in ingredients) {
                allIngredientSources.add(ingredient to recipeName)
            }
        }

        val merged = mutableMapOf<String, AutoGroceryItem>()
        for ((ingredient, recipeName) in allIngredientSources) {
            if (ingredient.name.lowercase().trim() in PANTRY_STAPLES) continue
            val key = ingredient.name.lowercase() + "_" + ingredient.category
            val existing = merged[key]
            if (existing != null) {
                val updatedSources = (existing.sourceRecipeNames + recipeName).distinct()
                merged[key] = existing.copy(
                    sourceRecipeNames = updatedSources,
                    quantity = existing.quantity ?: ingredient.quantity
                )
            } else {
                merged[key] = AutoGroceryItem(
                    name = ingredient.name,
                    quantity = ingredient.quantity,
                    category = ingredient.category,
                    sourceRecipeNames = listOfNotNull(recipeName.ifBlank { null })
                )
            }
        }

        return merged.values.sortedWith(compareBy({ it.category }, { it.name }))
    }

    // ── Share ──

    fun generateShareText(): String {
        val unchecked = autoItems.value.filter { it.key !in _checkedKeys.value }
        val grouped = unchecked.groupBy { it.category }
        val sortedCategories = grouped.keys.sortedBy { cat ->
            val idx = Categories.GROCERY.indexOf(cat.lowercase())
            if (idx >= 0) idx else 99
        }

        val sb = StringBuilder()
        sb.appendLine("Grocery List")
        sb.appendLine("============")
        for (category in sortedCategories) {
            val items = grouped[category] ?: continue
            sb.appendLine()
            sb.appendLine(category.replaceFirstChar { it.uppercase() })
            sb.appendLine("-".repeat(category.length))
            for (item in items) {
                val qty = if (!item.quantity.isNullOrBlank()) " (${item.quantity})" else ""
                sb.appendLine("- ${item.name}$qty")
            }
        }
        return sb.toString()
    }

    fun shareGroceryList(context: Context) {
        val text = generateShareText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Grocery List")
        }
        context.startActivity(Intent.createChooser(intent, "Share Grocery List"))
    }

    private fun getCurrentWeekStart(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
