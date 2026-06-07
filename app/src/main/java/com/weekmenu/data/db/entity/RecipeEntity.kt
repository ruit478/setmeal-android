package com.weekmenu.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: String,
    val lastUsedDate: String? = null, // ISO date (e.g. "2026-06-01")
    val cookCount: Int = 0
)
