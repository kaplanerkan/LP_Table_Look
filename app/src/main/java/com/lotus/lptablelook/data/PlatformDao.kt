package com.lotus.lptablelook.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lotus.lptablelook.model.Platform
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Query("SELECT * FROM platforms ORDER BY id ASC")
    fun getAllPlatforms(): Flow<List<Platform>>

    @Query("SELECT * FROM platforms ORDER BY id ASC")
    suspend fun getAllPlatformsSync(): List<Platform>

    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun getPlatformById(id: Int): Platform?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(platform: Platform): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(platforms: List<Platform>)

    @Update
    suspend fun update(platform: Platform)

    @Delete
    suspend fun delete(platform: Platform)

    @Query("DELETE FROM platforms")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM platforms")
    suspend fun getCount(): Int

    @Query("UPDATE platforms SET name = :name WHERE id = :id")
    suspend fun updateName(id: Int, name: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(platform: Platform): Long

    @Query("DELETE FROM platforms WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<Int>)

    /**
     * Merges the server's platform list into the local one without dropping the
     * table rows that reference these ids.
     */
    @Transaction
    suspend fun upsertFromServer(serverPlatforms: List<Platform>, deleteMissing: Boolean) {
        for (platform in serverPlatforms) {
            if (updateName(platform.id, platform.name) == 0) {
                insertIgnore(platform)
            }
        }
        if (deleteMissing) {
            deleteNotIn(serverPlatforms.map { it.id })
        }
    }
}
