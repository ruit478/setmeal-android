# WeekMenu

Weekly meal planner for Android — plan meals, batch cook, and generate grocery lists.

## What it does

1. **Add recipes** — name, category, ingredients with quantities
2. **Plan a week** — set work days, add meals with portion counts, auto-fill gaps from recipe bank
3. **Batch cooking** — multi-portion meals placed in contiguous slots with visual indicators
4. **Grocery list** — aggregated shopping list from plan recipes (auto) + manual items, pantry staples filtered

Local-first — SQLite via Room, no account needed.

## Screens

| Tab | What it does |
|-----|-------------|
| **Week** | 7-day grid (lunch/dinner). See current week's plan, tap to edit/reset, tap slots to move meals. Batch badges — 🍳 cooked, 📦 leftover. |
| **Recipes** | Recipe list with search + category filter. Add new recipes, edit ingredients, track cook count and last-used date. |
| **Grocery** | Shopping list for the current week. Auto-generated from plan (reactive — updates when you change the plan). Manual items tab for extras. Share via intent. |

## Features

- **Reactive grocery list** — updates immediately when plan is created, edited, or reset
- **Pantry filtering** — sal, pimenta, azeite, esparguete, arroz skipped from auto list
- **Batch cooking** — multi-portion claims placed in adjacent slots, grouped by batch number
- **Auto-fill** — fills remaining slots with least-recently-used recipes
- **Move meals** — drag-free reordering via tap-to-move dialog
- **Share** — plain-text grocery list via system share sheet

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **DI:** Hilt
- **Database:** Room (SQLite) with KSP
- **Navigation:** Navigation Compose
- **Min SDK:** 26 (Android 8.0)
- **Build:** Gradle, AGP 8.5

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

GitHub Actions builds on every push to `main`.

[![Build Debug APK](https://github.com/ruit478/weekmenu-android/actions/workflows/build.yml/badge.svg)](https://github.com/ruit478/weekmenu-android/actions/workflows/build.yml)

## Project structure

```
app/src/main/java/com/weekmenu/
├── data/
│   ├── Categories.kt           # Shared category constants
│   ├── db/
│   │   ├── entity/             # Room entities (Recipe, Slot, GroceryItem, etc.)
│   │   ├── dao/                # Data access objects
│   │   ├── seed/               # Seed data loader
│   │   ├── Converters.kt       # Room TypeConverters (LocalDate)
│   │   └── AppDatabase.kt      # Room database
├── di/
│   └── DatabaseModule.kt       # Hilt module
├── ui/
│   ├── week/                   # Week overview grid
│   ├── recipes/                # Recipe list, detail, add/edit
│   ├── grocery/                # Grocery list (auto + manual)
│   ├── override/               # Plan editor (work days, claims)
│   └── navigation/             # Nav graph + routes
├── WeekMenuApp.kt              # Application class (Hilt entry)
└── MainActivity.kt             # Single activity + scaffold
```
