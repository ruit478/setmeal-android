package com.weekmenu.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.weekmenu.ui.override.OverrideFormScreen
import com.weekmenu.ui.recipes.AddMealScreen
import com.weekmenu.ui.recipes.RecipeDetailScreen
import com.weekmenu.ui.week.WeekOverviewScreen
import com.weekmenu.ui.recipes.RecipeListScreen
import com.weekmenu.ui.grocery.GroceryListScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.WEEK
    ) {
        composable(Routes.WEEK) {
            WeekOverviewScreen(
                onNavigateToOverride = { weekStart -> navController.navigate(Routes.overrideForm(weekStart)) }
            )
        }

        composable(Routes.RECIPES) {
            RecipeListScreen(
                onNavigateToAddMeal = { navController.navigate(Routes.ADD_MEAL) },
                onNavigateToDetail = { recipeId ->
                    navController.navigate(Routes.recipeDetail(recipeId))
                }
            )
        }

        composable(Routes.GROCERY) {
            GroceryListScreen()
        }

        composable(
            route = Routes.OVERRIDE_FORM,
            arguments = listOf(navArgument("weekStart") { type = NavType.StringType })
        ) { backStackEntry ->
            val weekStart = backStackEntry.arguments?.getString("weekStart") ?: return@composable
            OverrideFormScreen(
                navController = navController,
                weekStartStr = weekStart
            )
        }

        composable(Routes.ADD_MEAL) {
            AddMealScreen(navController = navController)
        }

        composable(
            route = Routes.RECIPE_DETAIL,
            arguments = listOf(navArgument("recipeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: return@composable
            RecipeDetailScreen(
                navController = navController,
                recipeId = recipeId
            )
        }
    }
}
