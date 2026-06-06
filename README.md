# WeekMenu

Weekly meal planner for Android — auto-fill your week, batch cook efficiently, and generate grocery lists.

## What it does

Plan a full week of meals (lunch + dinner) with minimal effort:

1. **Add recipes** — name, ingredients, portion count
2. **Auto-fill** — fills empty slots with recipes in rotation, oldest-unused first
3. **Batch cooking** — multi-portion recipes automatically spread leftovers to adjacent slots
4. **Grocery list** — aggregates ingredients across the week, sorted by category

No account, no cloud — everything is local SQLite via Room.

## Screens

| Tab | What it does |
|-----|-------------|
| **Week** | 7-day grid (lunch/dinner). See the plan, tap to override, swipe between weeks. Auto-fill and batch grouping. |
| **Recipes** | Recipe list with search. Add new recipes, edit ingredients, track last-used week. |
| **Grocery** | Aggregated shopping list for the current week. Check off items, add manual extras. |

Detail screens: recipe detail (ingredients + edit), add meal, slot override picker, batch cooking breakdown.

## How the planner works

### Auto-fill

Sorts recipes by `lastUsedWeek` (null = never used → first). Fills empty slots in order, skipping recipes already used this week and avoiding same recipe in adjacent slots.

### Batch cooking

Recipes with `portions > 1` generate leftovers:
- Portion 1 → cooked slot (same day/meal)
- Portions 2+ → leftover slots (next meal, next day, etc.)

Leftovers chain: dinner → next day lunch → next day dinner.

Batch groups are numbered so you can see which meals share a cooking session.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **DI:** Hilt
- **Database:** Room (SQLite) with KSP
- **Navigation:** Navigation Compose
- **Serialization:** Gson (seed data)
- **Min SDK:** 26 (Android 8.0)
- **Build:** Gradle, AGP 8.x

## Build

### Local

```bash
git clone https://github.com/ruit478/weekmenu-android.git
cd weekmenu-android
./gradlew assembleDebug
```

Requires JDK 17.

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### CI

GitHub Actions builds on every push and PR to `main`.

[![Build Debug APK](https://github.com/ruit478/weekmenu-android/actions/workflows/build.yml/badge.svg)](https://github.com/ruit478/weekmenu-android/actions/workflows/build.yml)

## Project structure

```
app/src/main/java/com/weekmenu/
├── data/
│   ├── db/
│   │   ├── entity/          # Room entities (Recipe, Slot, GroceryItem, etc.)
│   │   ├── dao/             # Data access objects
│   │   ├── seed/            # Seed data loader
│   │   └── AppDatabase.kt   # Room database
│   └── planner/
│       └── MealPlanner.kt   # Auto-fill + batch grouping logic
├── di/
│   └── DatabaseModule.kt    # Hilt module
├── ui/
│   ├── week/                # Week overview grid
│   ├── recipes/             # Recipe list, detail, add/edit
│   ├── grocery/             # Grocery list
│   ├── override/            # Slot override picker
│   └── navigation/          # Nav graph + routes
├── WeekMenuApp.kt           # Application class (Hilt entry)
└── MainActivity.kt          # Single activity + scaffold
```
