package com.setmeal.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "grocery_items",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("planId")]
)
data class GroceryItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val name: String,
    val quantity: String? = null,
    val category: String,
    val checked: Boolean = false,
    val source: String // "auto" or "manual"
)
