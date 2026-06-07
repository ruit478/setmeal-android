package com.weekmenu.data.db.dao

import androidx.room.*
import com.weekmenu.data.db.entity.IngredientEntity
import com.weekmenu.data.db.entity.RecipeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes ORDER BY name ASC")
    suspend fun getAllRecipesList(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun getRecipeByIdFlow(id: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY sortOrder ASC")
    suspend fun getIngredientsForRecipe(recipeId: String): List<IngredientEntity>

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY sortOrder ASC")
    fun getIngredientsForRecipeFlow(recipeId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM recipes ORDER BY lastUsedDate ASC")
    suspend fun getRecipesByLeastRecentlyUsed(): List<RecipeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Update
    suspend fun updateRecipe(recipe: RecipeEntity)

    @Delete
    suspend fun deleteRecipe(recipe: RecipeEntity)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsForRecipe(recipeId: String)

    @Transaction
    suspend fun insertRecipeWithIngredients(recipe: RecipeEntity, ingredients: List<IngredientEntity>) {
        insertRecipe(recipe)
        insertIngredients(ingredients)
    }

    @Transaction
    suspend fun updateRecipeWithIngredients(recipe: RecipeEntity, ingredients: List<IngredientEntity>) {
        updateRecipe(recipe)
        deleteIngredientsForRecipe(recipe.id)
        insertIngredients(ingredients)
    }

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun getRecipeCount(): Int
}
