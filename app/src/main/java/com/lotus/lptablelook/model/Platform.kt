package com.lotus.lptablelook.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "platforms")
data class Platform(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String
) {
    @Ignore
    val tables: MutableList<Table> = mutableListOf()
}
