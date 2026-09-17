package com.lotus.lptablelook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT id FROM tables")
    suspend fun getAllIds(): List<Int>

    @Query("SELECT COUNT(*) FROM tables WHERE platformId = :platformId")
    suspend fun countByPlatform(platformId: Int): Int

    /**
     * Updates only the fields the server owns. Position, size, shape, chair style
     * and capacity belong to the user and must survive a sync.
     * Returns the number of affected rows (0 = table does not exist locally yet).
     */
    @Query("UPDATE tables SET name = :name, number = :number, platformId = :platformId WHERE id = :id")
    suspend fun updateServerFields(id: Int, name: String, number: Int, platformId: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(table: Table): Long

    @Query("DELETE FROM tables WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<Int>)

    /**
     * Merges the server's table list into the local one: existing rows keep their
     * layout, unknown rows are inserted, rows the server no longer reports are removed.
     */
    @Transaction
    suspend fun upsertFromServer(serverTables: List<Table>, deleteMissing: Boolean) {
        for (table in serverTables) {
            if (updateServerFields(table.id, table.name, table.number, table.platformId) == 0) {
                insertIgnore(table)
            }
        }
        if (deleteMissing) {
            deleteNotIn(serverTables.map { it.id })
        }
    }

    @Query("UPDATE tables SET positionX = :x, positionY = :y WHERE id = :tableId")
    suspend fun updatePosition(tableId: Int, x: Float, y: Float)

    @Query("UPDATE tables SET isOccupied = :occupied WHERE id = :tableId")
    suspend fun updateOccupied(tableId: Int, occupied: Boolean)

    @Query("UPDATE tables SET isOccupied = :occupied, waiterName = :waiterName, colorCode = :colorCode WHERE id = :tableId")
    suspend fun updateTableStatus(tableId: Int, occupied: Boolean, waiterName: String, colorCode: Int)

    @Query("UPDATE tables SET isOval = :isOval, capacity = :capacity, width = :width, height = :height, chairStyle = :chairStyle WHERE id = :tableId")
    suspend fun updateTableAppearance(tableId: Int, isOval: Boolean, capacity: Int, width: Float, height: Float, chairStyle: Int)

    @Query("UPDATE tables SET totalSum = :totalSum WHERE id = :tableId")
    suspend fun updateTableTotalSum(tableId: Int, totalSum: Double)

    @Query("UPDATE tables SET isOccupied = :occupied, waiterName = :waiterName, colorCode = :colorCode, totalSum = :totalSum WHERE id = :tableId")
    suspend fun updateTableStatusWithSum(tableId: Int, occupied: Boolean, waiterName: String, colorCode: Int, totalSum: Double)
}
