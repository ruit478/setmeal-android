package com.weekmenu.ui.grocery

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weekmenu.data.Categories
import com.weekmenu.data.db.dao.GroceryDao
import com.weekmenu.data.db.dao.RecipeDao
import com.weekmenu.data.db.dao.WeeklyPlanDao
import com.weekmenu.data.db.entity.IngredientEntity
import com.weekmenu.data.db.entity.ManualGroceryItemEntity
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

    companion object {
        /** Ingredients that are assumed always available — skipped from auto-generated list. */
        private val PANTRY_STAPLES = setOf(
            "sal", "pimenta", "azeite", "esparguete", "arroz"
        )
    }

    // ── Manual items (persisted in DB) ──

    val manualItems: StateFlow<List<ManualGroceryItemEntity>> = groceryDao.getAllManualItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Week navigation ──

    private val _currentWeekStart = MutableStateFlow(getCurrentWeekStart())
    val currentWeekStart: StateFlow<LocalDate> = _currentWeekStart.asStateFlow()

    val weekEnd: StateFlow<LocalDate> = _currentWeekStart
        .map { it.plusDays(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _currentWeekStart.value.plusDays(6))

    // ── Auto items (reactive to week changes) ──

    val autoItems: StateFlow<List<AutoGroceryItem>> = _currentWeekStart
        .flatMapLatest { weekStart -> loadAutoItemsForWeek(weekStart) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Set of auto-item keys that have been checked by the user (ephemeral). */
    private val _checkedAutoKeys = MutableStateFlow<Set<String>>(emptySet())
    val checkedAutoKeys: StateFlow<Set<String>> = _checkedAutoKeys.asStateFlow()

    private fun loadAutoItemsForWeek(weekStart: LocalDate): Flow<List<AutoGroceryItem>> = flow {
        val plan = weeklyPlanDao.getPlanByWeekStart(weekStart.toString()).first()
        if (plan == null) {
            emit(emptyList())
            return@flow
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

        // Merge by name+category, filtering pantry staples
        val merged = mutableMapOf<String, AutoGroceryItem>()
        for ((ingredient, recipeName) in allIngredientSources) {
            // Skip pantry staples
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

        emit(
            merged.values.sortedWith(
                compareBy({ it.category }, { it.name })
            )
        )
    }

    // ── Week navigation ──

    fun previousWeek() {
        _currentWeekStart.value = _currentWeekStart.value.minusWeeks(1)
        _checkedAutoKeys.value = emptySet()
    }

    fun nextWeek() {
        _currentWeekStart.value = _currentWeekStart.value.plusWeeks(1)
        _checkedAutoKeys.value = emptySet()
    }

    fun resetToCurrentWeek() {
        _currentWeekStart.value = getCurrentWeekStart()
        _checkedAutoKeys.value = emptySet()
    }

    private fun getCurrentWeekStart(): LocalDate =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

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

    // ── Clear ──

    /** Delete all manual items from the database. */
    fun clearManualItems() {
        viewModelScope.launch {
            groceryDao.deleteAllManualItems()
        }
    }

    /** Uncheck all auto-generated items (ephemeral — doesn't touch DB). */
    fun clearAutoChecks() {
        _checkedAutoKeys.value = emptySet()
    }

    /** Clear both manual items and auto checkboxes. */
    fun clearAll() {
        viewModelScope.launch {
            groceryDao.deleteAllManualItems()
            _checkedAutoKeys.value = emptySet()
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
        val sortedCategories = grouped.keys.sortedBy { cat ->
            val idx = Categories.GROCERY.indexOf(cat.lowercase())
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
