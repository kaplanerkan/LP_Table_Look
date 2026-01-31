package com.lotus.lptablelook.data

import com.lotus.lptablelook.model.Platform
import com.lotus.lptablelook.model.Settings
import com.lotus.lptablelook.model.Table
import kotlinx.coroutines.flow.Flow

class TableRepository(private val database: AppDatabase) {

    private val platformDao = database.platformDao()
    private val tableDao = database.tableDao()
    private val settingsDao = database.settingsDao()

    // Platform operations
    fun getAllPlatforms(): Flow<List<Platform>> = platformDao.getAllPlatforms()

    suspend fun getPlatformById(id: Int): Platform? = platformDao.getPlatformById(id)

    suspend fun insertPlatform(platform: Platform): Long = platformDao.insert(platform)

    suspend fun insertPlatforms(platforms: List<Platform>) = platformDao.insertAll(platforms)

    suspend fun updatePlatform(platform: Platform) = platformDao.update(platform)

    suspend fun deletePlatform(platform: Platform) = platformDao.delete(platform)

    suspend fun deleteAllPlatforms() = platformDao.deleteAll()

    suspend fun getPlatformCount(): Int = platformDao.getCount()

    suspend fun getAllPlatformsSync(): List<Platform> = platformDao.getAllPlatformsSync()

    // Table operations
    fun getAllTables(): Flow<List<Table>> = tableDao.getAllTables()

    fun getTablesByPlatform(platformId: Int): Flow<List<Table>> = tableDao.getTablesByPlatform(platformId)

    suspend fun getTablesByPlatformSync(platformId: Int): List<Table> = tableDao.getTablesByPlatformSync(platformId)

    suspend fun getTableById(id: Int): Table? = tableDao.getTableById(id)

    suspend fun insertTable(table: Table): Long = tableDao.insert(table)

    suspend fun insertTables(tables: List<Table>) = tableDao.insertAll(tables)

    suspend fun updateTable(table: Table) = tableDao.update(table)

    suspend fun deleteTable(table: Table) = tableDao.delete(table)

    suspend fun deleteAllTables() = tableDao.deleteAll()

    suspend fun deleteTablesByPlatform(platformId: Int) = tableDao.deleteByPlatform(platformId)

    suspend fun updateTablePosition(tableId: Int, x: Float, y: Float) = tableDao.updatePosition(tableId, x, y)

    suspend fun updateTableOccupied(tableId: Int, occupied: Boolean) = tableDao.updateOccupied(tableId, occupied)

    suspend fun updateTableStatus(tableId: Int, occupied: Boolean, waiterName: String, colorCode: Int) =
        tableDao.updateTableStatus(tableId, occupied, waiterName, colorCode)

    suspend fun updateTableAppearance(tableId: Int, isOval: Boolean, capacity: Int, width: Float, height: Float, chairStyle: Int) =
        tableDao.updateTableAppearance(tableId, isOval, capacity, width, height, chairStyle)

    // Initialize default data
    suspend fun initializeDefaultData() {
        if (getPlatformCount() == 0) {
            val platforms = listOf(
                Platform(id = 1, name = "Haupt"),
                Platform(id = 2, name = "Terrasse"),
                Platform(id = 3, name = "Hinten"),
                Platform(id = 4, name = "Bar"),
                Platform(id = 5, name = "VIP")
            )
            insertPlatforms(platforms)

            val allTables = mutableListOf<Table>()

            // Haupt - 10 tables
            allTables.addAll(generateTables(1, 1, 10))
            // Terrasse - 8 tables
            allTables.addAll(generateTables(2, 11, 8))
            // Hinten - 6 tables
            allTables.addAll(generateTables(3, 19, 6))
            // Bar - 4 tables
            allTables.addAll(generateTables(4, 25, 4))
            // VIP - 2 tables
            allTables.addAll(generateTables(5, 29, 2))

            insertTables(allTables)
        }
    }

    private fun generateTables(platformId: Int, startNumber: Int, count: Int): List<Table> {
        val tables = mutableListOf<Table>()
        val columns = 5
        val startX = 80f
        val startY = 80f
        val spacingX = 180f
        val spacingY = 160f

        for (i in 0 until count) {
            val row = i / columns
            val col = i % columns
            val capacity = if (i % 3 == 0) 4 else 2
            val tableNumber = startNumber + i

            tables.add(
                Table(
                    id = tableNumber,
                    name = "Tisch $tableNumber",
                    number = tableNumber,
                    capacity = capacity,
                    positionX = startX + (col * spacingX),
                    positionY = startY + (row * spacingY),
                    width = if (capacity == 2) 100f else 140f,
                    height = if (capacity == 2) 80f else 100f,
                    isOccupied = false,
                    platformId = platformId
                )
            )
        }
        return tables
    }

    // Settings operations
    fun getSettings(): Flow<Settings?> = settingsDao.getSettings()

    suspend fun getSettingsSync(): Settings? = settingsDao.getSettingsSync()

    suspend fun saveSettings(settings: Settings) = settingsDao.insert(settings)

    suspend fun updateSettings(settings: Settings) = settingsDao.update(settings)

    suspend fun updateSocketConfig(ip: String, port: Int) = settingsDao.updateSocketConfig(ip, port)

    suspend fun initializeSettings() {
        val existing = getSettingsSync()
        android.util.Log.d("TableRepository", "initializeSettings - existing: $existing")
        if (existing == null) {
            saveSettings(Settings())
        }
    }
}
