package com.setmeal.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Week : Screen("week", "Semana", Icons.Default.CalendarMonth)
    data object Recipes : Screen("recipes", "Refeições", Icons.Default.Restaurant)
    data object Grocery : Screen("grocery", "Compras", Icons.Default.ShoppingCart)
}

object Routes {
    const val WEEK = "week"
    const val RECIPES = "recipes"
    const val GROCERY = "grocery"
    const val ADD_MEAL = "add_meal"
    const val RECIPE_DETAIL = "recipe_detail/{recipeId}"
    const val OVERRIDE_FORM = "override_form"
    const val BATCH_VIEW = "batch_view/{planId}"

    fun recipeDetail(recipeId: String) = "recipe_detail/$recipeId"
    fun batchView(planId: String) = "batch_view/$planId"
}

val bottomNavItems = listOf(
    Screen.Week,
    Screen.Recipes,
    Screen.Grocery
)
