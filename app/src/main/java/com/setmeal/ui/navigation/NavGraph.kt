package com.setmeal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.setmeal.ui.recipes.AddMealScreen
import com.setmeal.ui.recipes.RecipeDetailScreen
import com.setmeal.ui.week.WeekOverviewScreen
import com.setmeal.ui.recipes.RecipeListScreen
import com.setmeal.ui.grocery.GroceryListScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.WEEK
    ) {
        composable(Routes.WEEK) {
            WeekOverviewScreen(
                onNavigateToOverride = { navController.navigate(Routes.OVERRIDE_FORM) },
                onNavigateToBatch = { planId -> navController.navigate(Routes.batchView(planId)) }
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

        composable(Routes.OVERRIDE_FORM) {
            // TODO Phase 3: OverrideFormScreen
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

        composable(Routes.BATCH_VIEW) {
            // TODO Phase 5: BatchViewScreen
        }
    }
}
