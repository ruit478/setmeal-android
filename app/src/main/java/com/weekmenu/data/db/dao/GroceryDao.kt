package com.weekmenu.data.db.dao

import androidx.room.*
import com.weekmenu.data.db.entity.GroceryItemEntity
import com.weekmenu.data.db.entity.ManualGroceryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_items WHERE planId = :planId ORDER BY category ASC, name ASC")
    fun getGroceryItemsForPlan(planId: String): Flow<List<GroceryItemEntity>>

    @Query("SELECT * FROM grocery_items WHERE planId = :planId ORDER BY category ASC, name ASC")
    suspend fun getGroceryItemsForPlanList(planId: String): List<GroceryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroceryItems(items: List<GroceryItemEntity>)

    @Query("UPDATE grocery_items SET checked = :checked WHERE id = :id")
    suspend fun updateGroceryItemChecked(id: String, checked: Boolean)

    @Query("DELETE FROM grocery_items WHERE planId = :planId")
    suspend fun deleteGroceryItemsForPlan(planId: String)

    // Manual grocery items (persistent across weeks)
    @Query("SELECT * FROM manual_grocery_items ORDER BY category ASC, name ASC")
    fun getAllManualItems(): Flow<List<ManualGroceryItemEntity>>

    @Query("SELECT * FROM manual_grocery_items ORDER BY category ASC, name ASC")
    suspend fun getAllManualItemsList(): List<ManualGroceryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManualItem(item: ManualGroceryItemEntity)

    @Update
    suspend fun updateManualItem(item: ManualGroceryItemEntity)

    @Delete
    suspend fun deleteManualItem(item: ManualGroceryItemEntity)

    @Query("DELETE FROM manual_grocery_items WHERE checked = 1")
    suspend fun clearCheckedManualItems()

    @Query("DELETE FROM manual_grocery_items")
    suspend fun deleteAllManualItems()
}
