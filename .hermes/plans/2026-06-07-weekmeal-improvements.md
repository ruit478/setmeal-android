# WeekMenu Improvements Implementation Plan

> **For Hermes:** Execute task-by-task via CI. Each phase = commit + push + APK delivery.

**Goal:** Fix critical bugs, implement batch cooking visualization, pantry filtering, and code cleanup.

**Architecture:** Room DB + Jetpack Compose MVVM (Hilt ViewModels). CI-only build on ARM64 Pi.

**Tech Stack:** Kotlin 2.0, Compose BOM 2024.06, Room 2.6.1, Hilt 2.51.1, AGP 8.5.2

---

## Phase 1: Critical Data Fixes

### Task 1.1: Fix lastUsedWeek wrapping at year boundary

**Objective:** Replace `Int?` with `LocalDate?` for `lastUsedWeek` so year boundaries don't break.

**Files:**
- Modify: `app/src/main/java/com/weekmenu/data/db/entity/RecipeEntity.kt`
- Modify: `app/src/main/java/com/weekmenu/data/db/dao/RecipeDao.kt`
- Modify: `app/src/main/java/com/weekmenu/ui/override/OverrideViewModel.kt`
- Modify: `app/src/main/java/com/weekmenu/ui/recipes/RecipeDetailScreen.kt`

**Step 1:** Change RecipeEntity.lastUsedWeek from `Int?` to `String?` (store ISO date string, Room can't directly store LocalDate without TypeConverter)

```kotlin
// RecipeEntity.kt
@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val lastUsedDate: String? = null,  // "2026-06-01" ISO format, nullable
    val cookCount: Int = 0
)
```

**Step 2:** Add TypeConverters for LocalDate

Create: `app/src/main/java/com/weekmenu/data/db/Converters.kt`

```kotlin
package com.weekmenu.data.db

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? = 
        dateString?.let { LocalDate.parse(it) }
}
```

**Step 3:** Register converters on AppDatabase

```kotlin
@TypeConverter
@Database(..., version = 2, ...)
abstract class AppDatabase : RoomDatabase() {
    // ...
}
```

Add `@TypeConverters(Converters::class)` annotation.

**Step 4:** Update RecipeDao — change `lastUsedWeek` references to `lastUsedDate`, update `getRecipesByLeastRecentlyUsed()` ordering

```kotlin
@Query("SELECT * FROM recipes ORDER BY lastUsedDate ASC")
suspend fun getRecipesByLeastRecentlyUsed(): List<RecipeEntity>
```

**Step 5:** Update OverrideViewModel.save() — set `lastUsedDate` to `weekStart` (Monday of week plan), increment `cookCount`

**Step 6:** Update RecipeDetailScreen — display `lastUsedDate` instead of `lastUsedWeek`

**Step 7:** DB migration v1→v2: drop `lastUsedWeek` column. Use `fallbackToDestructiveMigration()` for dev simplicity (version bump).

### Task 1.2: Increment cookCount when recipes used

**Objective:** In OverrideViewModel.save(), increment `cookCount` for each recipe used in the plan.

**Files:**
- Modify: `app/src/main/java/com/weekmenu/ui/override/OverrideViewModel.kt`

In the save() function's recipe usage loop:

```kotlin
// After auto-fill or claimed recipe placement:
recipeDao.updateRecipe(recipe.copy(
    lastUsedDate = state.weekStart,
    cookCount = recipe.cookCount + 1
))
```

Also increment for claimed recipes matched by name in `allRecipes`.

---

## Phase 2: Grocery List Reactivity

### Task 2.1: Make GroceryViewModel react to week navigation

**Objective:** GroceryViewModel should observe the current week's plan reactively, not use a fixed `weekStart`.

**Files:**
- Modify: `app/src/main/java/com/weekmenu/ui/grocery/GroceryViewModel.kt`

**Step 1:** Replace fixed `weekStart` with a `MutableStateFlow<LocalDate>` navigable week

```kotlin
private val _currentWeekStart = MutableStateFlow(
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
)
```

**Step 2:** Use `flatMapLatest` to reactively load auto items when week changes

```kotlin
init {
    viewModelScope.launch {
        _currentWeekStart
            .flatMapLatest { weekStart ->
                flow {
                    val plan = weeklyPlanDao.getPlanByWeekStart(weekStart.toString()).first()
                    if (plan == null) {
                        emit(emptyList())
                    } else {
                        // load and merge...
                        emit(mergedList)
                    }
                }
            }
            .collect { _autoItems.value = it }
    }
}
```

**Step 3:** Add `previousWeek()` / `nextWeek()` / `resetToCurrentWeek()` navigation methods matching WeekViewModel pattern.

**Step 4:** Add week navigation UI to GroceryListScreen (arrow buttons in header, matching WeekOverview pattern).

### Task 2.2: Filter pantry staples from grocery auto items

**Objective:** Skip sal, pimenta, azeite, esparguete, arroz from auto-generated grocery list.

**Files:**
- Modify: `app/src/main/java/com/weekmenu/ui/grocery/GroceryViewModel.kt`

Add a constant set and filter:

```kotlin
companion object {
    val PANTRY_STAPLES = setOf("sal", "pimenta", "azeite", "esparguete", "arroz")
}
```

Filter after merging ingredients — skip any item whose lowercased name is in `PANTRY_STAPLES`.

---

## Phase 3: Batch Cooking

### Task 3.1: Use MealPlanner.groupBatchClaims in save flow

**Objective:** Replace the shuffle-and-distribute logic in OverrideViewModel.save() with proper batch-aware distribution using the existing MealPlanner.

**Files:**
- Modify: `app/src/main/java/com/weekmenu/ui/override/OverrideViewModel.kt`

**Step 1:** Instead of shuffling portions into a pool, build `ClaimInput` objects and call `MealPlanner.groupBatchClaims()`.

**Step 2:** For claims with `portionCount > 1`, use `groupBatchClaims` to produce `BatchClaimResult` with `batchGroup` and `batchTotal` set.

**Step 3:** Fill remaining empty slots with `MealPlanner.autoFillSlots()` using recipes from `getRecipesByLeastRecentlyUsed()`.

**Step 4:** Convert results to `SlotEntity` with proper `batchGroup`/`batchTotal`/`slotType` values.

### Task 3.2: Show batch cooking indicators on week overview

**Objective:** Display batch group badges on meal rows in WeekOverviewScreen.

**Files:**
- Modify: `app/src/main/java/com/weekmenu/ui/week/WeekOverviewScreen.kt`
- Modify: `app/src/main/java/com/weekmenu/ui/week/WeekViewModel.kt`

**Step 1:** Add `batchGroup` and `batchTotal` to `WeekSummary` data class.

**Step 2:** In `WeekViewModel.weekGrid` mapping, pass `batchGroup`/`batchTotal` from `SlotEntity` into `WeekSummary`.

**Step 3:** In `MealRow` composable, show a small badge when `batchGroup != null`:
- "🍳" + group number (e.g., "B1") for the first portion (cooked)
- "📦 L" for leftover portions

Or simpler: colored dot + tooltip showing group info.

---

## Phase 4: Code Quality

### Task 4.1: Extract hardcoded categories to a shared constants file

**Objective:** Single source of truth for recipe and ingredient categories.

**Files:**
- Create: `app/src/main/java/com/weekmenu/data/Categories.kt`

```kotlin
package com.weekmenu.data

object Categories {
    val RECIPE_CATEGORIES = listOf("carne", "vegetariano", "air-fryer", "seitan", "peixe", "sopa", "outros")
    val INGREDIENT_CATEGORIES = listOf("carne", "vegetais", "mercearia", "congelados", "laticinios", "fruta", "outros")
    val GROCERY_CATEGORIES = listOf("meat", "vegetables", "pantry", "frozen", "dairy", "fruit", "other")
    
    val GROCERY_CATEGORY_LABELS = mapOf(
        "meat" to "Meat",
        "vegetables" to "Vegetables",
        "pantry" to "Pantry",
        "frozen" to "Frozen",
        "dairy" to "Dairy",
        "fruit" to "Fruit",
        "other" to "Other"
    )
}
```

- Modify: `app/src/main/java/com/weekmenu/ui/recipes/AddMealScreen.kt` — import from Categories
- Modify: `app/src/main/java/com/weekmenu/ui/recipes/RecipeDetailScreen.kt` — import from Categories
- Modify: `app/src/main/java/com/weekmenu/ui/grocery/GroceryListScreen.kt` — import from Categories
- Modify: `app/src/main/java/com/weekmenu/ui/recipes/AddMealViewModel.kt` — default category from Categories

### Task 4.2: Remove unused imports and minor cleanup

**Objective:** Clean imports, remove unused MealPlanner.kt typealiases if not used.

**Files:**
- Scan all .kt files for unused imports

---

## Execution Order

Phase 1 → Phase 2 → Phase 3 → Phase 4

Each phase: commit + push + CI build + deliver APK.

**Total estimated: 4 phases, ~8 tasks.**
