package com.weekmenu.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "manual_grocery_items")
data class ManualGroceryItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: String? = null,
    val category: String,
    val checked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
