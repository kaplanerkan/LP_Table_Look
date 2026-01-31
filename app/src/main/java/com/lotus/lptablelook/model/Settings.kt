package com.lotus.lptablelook.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val FIXED_PORT = 1453

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = 1,
    val socketIp: String = "",
    val socketPort: Int = FIXED_PORT,
    val restaurantName: String = "",
    val autoConnect: Boolean = false,
    val tableScaleDefault: Float = 1.0f
)
