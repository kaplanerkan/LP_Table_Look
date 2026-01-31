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
    val tableScaleDefault: Float = 1.0f,
    val backgroundStyle: Int = 0,  // 0=Blue, 1=Warm, 2=Dark, 3=Green, 4=Purple
    val showChairs: Boolean = true,  // Show chairs around tables (default: true)
    val showPrices: Boolean = true,  // Show price badges on occupied tables (default: true)
    val showCurrencySymbol: Boolean = true  // Show € symbol in price badges (default: true)
)
