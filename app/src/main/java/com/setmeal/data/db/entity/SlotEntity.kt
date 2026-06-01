package com.setmeal.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "slots",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("planId"), Index("recipeId")]
)
data class SlotEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val dayOfWeek: Int, // 0=Segunda .. 6=Domingo
    val mealTime: String, // "lunch" or "dinner"
    val slotType: String, // "claimed", "auto_fill", "work", "leftover"
    val recipeId: String? = null,
    val recipeName: String? = null, // denormalized for display
    val batchGroup: Int? = null,
    val batchTotal: Int? = null,
    val sortOrder: Int = 0
)
