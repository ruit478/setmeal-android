package com.weekmenu.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "weekly_plans")
data class WeeklyPlanEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val weekStart: String, // ISO date string, always Monday
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null
) {
    fun toLocalDate(): LocalDate = LocalDate.parse(weekStart)
}
