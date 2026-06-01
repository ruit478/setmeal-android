package com.setmeal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.setmeal.data.db.dao.GroceryDao
import com.setmeal.data.db.dao.RecipeDao
import com.setmeal.data.db.dao.WeeklyPlanDao
import com.setmeal.data.db.entity.*
import com.setmeal.data.db.seed.SeedDataLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        WeeklyPlanEntity::class,
        SlotEntity::class,
        GroceryItemEntity::class,
        ManualGroceryItemEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun weeklyPlanDao(): WeeklyPlanDao
    abstract fun groceryDao(): GroceryDao

    companion object {
        private const val DB_NAME = "setmeal.db"

        fun createAndSeed(context: Context, scope: CoroutineScope): AppDatabase {
            val database = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            ).build()

            // Seed data on first launch
            scope.launch(Dispatchers.IO) {
                SeedDataLoader.seedIfEmpty(database, context)
            }

            return database
        }
    }
}
