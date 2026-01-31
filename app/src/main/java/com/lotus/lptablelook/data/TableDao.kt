package com.lotus.lptablelook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lotus.lptablelook.model.Table
import kotlinx.coroutines.flow.Flow

@Dao
interface TableDao {

    @Query("SELECT * FROM tables ORDER BY number ASC")
    fun getAllTables(): Flow<List<Table>>

    @Query("SELECT * FROM tables WHERE platformId = :platformId ORDER BY number ASC")
    fun getTablesByPlatform(platformId: Int): Flow<List<Table>>

    @Query("SELECT * FROM tables WHERE platformId = :platformId ORDER BY number ASC")
    suspend fun getTablesByPlatformSync(platformId: Int): List<Table>

    @Query("SELECT * FROM tables WHERE id = :id")
    suspend fun getTableById(id: Int): Table?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(table: Table): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tables: List<Table>)

    @Update
    suspend fun update(table: Table)

    @Delete
    suspend fun delete(table: Table)

    @Query("DELETE FROM tables WHERE platformId = :platformId")
    suspend fun deleteByPlatform(platformId: Int)

    @Query("DELETE FROM tables")
    suspend fun deleteAll()

    @Query("UPDATE tables SET positionX = :x, positionY = :y WHERE id = :tableId")
    suspend fun updatePosition(tableId: Int, x: Float, y: Float)

    @Query("UPDATE tables SET isOccupied = :occupied WHERE id = :tableId")
    suspend fun updateOccupied(tableId: Int, occupied: Boolean)

    @Query("UPDATE tables SET isOccupied = :occupied, waiterName = :waiterName, colorCode = :colorCode WHERE id = :tableId")
    suspend fun updateTableStatus(tableId: Int, occupied: Boolean, waiterName: String, colorCode: Int)

    @Query("UPDATE tables SET isOval = :isOval, capacity = :capacity, width = :width, height = :height WHERE id = :tableId")
    suspend fun updateTableAppearance(tableId: Int, isOval: Boolean, capacity: Int, width: Float, height: Float)
}
