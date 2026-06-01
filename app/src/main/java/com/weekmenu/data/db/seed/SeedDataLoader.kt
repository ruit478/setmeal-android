package com.weekmenu.data.db.seed

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.weekmenu.data.db.AppDatabase
import com.weekmenu.data.db.entity.IngredientEntity
import com.weekmenu.data.db.entity.RecipeEntity
import java.io.BufferedReader

data class SeedRecipe(
    val name: String,
    val category: String,
    val ingredients: List<SeedIngredient>
)

data class SeedIngredient(
    val name: String,
    val quantity: String?,
    val category: String
)

object SeedDataLoader {

    private fun loadRecipes(context: Context): List<SeedRecipe> {
        val json = context.assets.open("seed_data.json")
            .bufferedReader()
            .use(BufferedReader::readText)

        val type = object : TypeToken<List<SeedRecipe>>() {}.type
        return Gson().fromJson(json, type)
    }

    suspend fun seedIfEmpty(database: AppDatabase, context: Context) {
        val dao = database.recipeDao()
        if (dao.getRecipeCount() > 0) return

        val recipes = loadRecipes(context)

        recipes.forEach { seedRecipe ->
            val recipeEntity = RecipeEntity(
                name = seedRecipe.name,
                category = seedRecipe.category,
                lastUsedWeek = null,
                cookCount = 0
            )
            val ingredients = seedRecipe.ingredients.mapIndexed { i, ing ->
                IngredientEntity(
                    recipeId = recipeEntity.id,
                    name = ing.name,
                    quantity = ing.quantity,
                    category = ing.category,
                    sortOrder = i
                )
            }
            dao.insertRecipeWithIngredients(recipeEntity, ingredients)
        }
    }
}
