package com.weekmenu.di

import android.content.Context
import com.weekmenu.data.db.AppDatabase
import com.weekmenu.data.db.dao.GroceryDao
import com.weekmenu.data.db.dao.RecipeDao
import com.weekmenu.data.db.dao.WeeklyPlanDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.createAndSeed(context, CoroutineScope(SupervisorJob()))
    }

    @Provides
    fun provideRecipeDao(database: AppDatabase): RecipeDao = database.recipeDao()

    @Provides
    fun provideWeeklyPlanDao(database: AppDatabase): WeeklyPlanDao = database.weeklyPlanDao()

    @Provides
    fun provideGroceryDao(database: AppDatabase): GroceryDao = database.groceryDao()
}
