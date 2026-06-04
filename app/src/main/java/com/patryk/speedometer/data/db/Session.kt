package com.patryk.speedometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long = 0,
    val label: String = "",
    val maxSpeedMps: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val distanceM: Double = 0.0,
)
