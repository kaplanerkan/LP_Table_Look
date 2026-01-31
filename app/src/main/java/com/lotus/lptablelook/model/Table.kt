package com.lotus.lptablelook.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tables",
    indices = [Index("platformId")]
)
data class Table(
    @PrimaryKey
    val id: Int = 0,
    val name: String = "",
    val number: Int = 0,
    val capacity: Int = 4,
    var positionX: Float = 0f,
    var positionY: Float = 0f,
    var width: Float = 140f,
    var height: Float = 100f,
    var isOccupied: Boolean = false,
    val platformId: Int = 1,
    var waiterName: String = "",
    var colorCode: Int = 0  // 0=green, 1=orange, 2=blue
)
