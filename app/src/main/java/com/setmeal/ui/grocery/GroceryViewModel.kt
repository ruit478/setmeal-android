package com.setmeal.ui.grocery

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.setmeal.data.db.dao.GroceryDao
import com.setmeal.data.db.dao.RecipeDao
import com.setmeal.data.db.dao.WeeklyPlanDao
import com.setmeal.data.db.entity.IngredientEntity
import com.setmeal.data.db.entity.ManualGroceryItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/** An auto-aggregated grocery item derived from plan recipe ingredients. */
data class AutoGroceryItem(
    val name: String,
    val quantity: String?,
    val category: String,
    val sourceRecipeNames: List<String>,
    /** Composite key used for dedup and checked-state tracking. */
    val key: String = "${name.lowercase()}_${category}"
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GroceryViewModel @Inject constructor(
    private val groceryDao: GroceryDao,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val recipeDao: RecipeDao
) : ViewModel() {

    // ── Manual items (persisted in DB) ──

    val manualItems: StateFlow<List<ManualGroceryItemEntity>> = groceryDao.getAllManualItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Auto items (computed from current week plan) ──

    /** The start of the current week (Monday). */
    private val weekStart: LocalDate = LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private val _autoItems = MutableStateFlow<List<AutoGroceryItem>>(emptyList())
    val autoItems: StateFlow<List<AutoGroceryItem>> = _autoItems.asStateFlow()

    /** Set of auto-item keys that have been checked by the user (ephemeral). */
    private val _checkedAutoKeys = MutableStateFlow<Set<String>>(emptySet())
    val checkedAutoKeys: StateFlow<Set<String>> = _checkedAutoKeys.asStateFlow()

    init {
        loadAutoItems()
    }

    private fun loadAutoItems() {
        viewModelScope.launch {
            val plan = weeklyPlanDao.getPlanByWeekStart(weekStart.toString())
            if (plan == null) {
                _autoItems.value = emptyList()
                return@launch
            }

            val slots = weeklyPlanDao.getSlotsForPlanList(plan.id)
            val recipeIds = slots.mapNotNull { it.recipeId }.distinct()

            // For each recipe, load ingredients and recipe name
            val allIngredientSources = mutableListOf<Pair<IngredientEntity, String>>()
            for (recipeId in recipeIds) {
                val recipe = recipeDao.getRecipeById(recipeId)
                val recipeName = recipe?.name ?: ""
                val ingredients = recipeDao.getIngredientsForRecipe(recipeId)
                for (ingredient in ingredients) {
                    allIngredientSources.add(ingredient to recipeName)
                }
            }

            // Merge by name+category: sum quantities (take first if different) and aggregate source names
            val merged = mutableMapOf<String, AutoGroceryItem>()
            for ((ingredient, recipeName) in allIngredientSources) {
                val key = ingredient.name.lowercase() + "_" + ingredient.category
                val existing = merged[key]
                if (existing != null) {
                    // Update source recipe names (dedup)
                    val updatedSources = (existing.sourceRecipeNames + recipeName).distinct()
                    merged[key] = existing.copy(
                        sourceRecipeNames = updatedSources,
                        // Keep first quantity, or could try to merge numeric values
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

            _autoItems.value = merged.values.sortedWith(
                compareBy({ it.category }, { it.name })
            )
        }
    }

    // ── Manual item actions ──

    fun addManualItem(name: String, quantity: String?, category: String) {
        viewModelScope.launch {
            groceryDao.insertManualItem(
                ManualGroceryItemEntity(
                    name = name,
                    quantity = quantity,
                    category = category
                )
            )
        }
    }

    fun toggleManualItem(item: ManualGroceryItemEntity) {
        viewModelScope.launch {
            groceryDao.updateManualItem(item.copy(checked = !item.checked))
        }
    }

    fun deleteManualItem(item: ManualGroceryItemEntity) {
        viewModelScope.launch {
            groceryDao.deleteManualItem(item)
        }
    }

    // ── Auto item checked state ──

    fun toggleAutoItem(item: AutoGroceryItem) {
        val current = _checkedAutoKeys.value.toMutableSet()
        if (current.contains(item.key)) {
            current.remove(item.key)
        } else {
            current.add(item.key)
        }
        _checkedAutoKeys.value = current
    }

    // ── Clear checked ──

    fun clearChecked(onlyManual: Boolean = true) {
        viewModelScope.launch {
            groceryDao.clearCheckedManualItems()
            if (!onlyManual) {
                _checkedAutoKeys.value = emptySet()
            }
        }
    }

    // ── Share ──

    /**
     * Builds a plain-text grocery list from all unchecked items (manual + auto),
     * grouped by category, formatted for sharing via Intent.
     */
    fun generateShareText(): String {
        val uncheckedManual = manualItems.value.filter { !it.checked }
        val uncheckedAuto = autoItems.value.filter { it.key !in _checkedAutoKeys.value }

        val sb = StringBuilder()
        sb.appendLine("Grocery List")
        sb.appendLine("============")

        // Collect all items grouped by category
        val allItems = mutableListOf<GroupedItem>()

        for (item in uncheckedManual) {
            allItems.add(
                GroupedItem(
                    name = item.name,
                    quantity = item.quantity,
                    category = item.category,
                    source = "manual"
                )
            )
        }

        for (item in uncheckedAuto) {
            allItems.add(
                GroupedItem(
                    name = item.name,
                    quantity = item.quantity,
                    category = item.category,
                    source = item.sourceRecipeNames.joinToString(", ")
                )
            )
        }

        val grouped = allItems.groupBy { it.category }
        val categoryOrder = listOf("meat", "vegetables", "pantry", "frozen", "dairy", "fruit", "other")
        val sortedCategories = grouped.keys.sortedBy { cat ->
            val idx = categoryOrder.indexOf(cat.lowercase())
            if (idx >= 0) idx else 99
        }

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

    /** Launches a share intent with the grocery list. */
    fun shareGroceryList(context: Context) {
        val text = generateShareText()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Grocery List")
        }
        context.startActivity(Intent.createChooser(intent, "Share Grocery List"))
    }

    private data class GroupedItem(
        val name: String,
        val quantity: String?,
        val category: String,
        val source: String
    )
}
