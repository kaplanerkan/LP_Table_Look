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

    suspend fun updateTableStatusWithSum(tableId: Int, occupied: Boolean, waiterName: String, colorCode: Int, totalSum: Double) =
        tableDao.updateTableStatusWithSum(tableId, occupied, waiterName, colorCode, totalSum)

    suspend fun updateTableTotalSum(tableId: Int, totalSum: Double) =
        tableDao.updateTableTotalSum(tableId, totalSum)

    /**
     * Merges the server's table list into the local DB. Tables we already know keep
     * their user-defined layout (position, size, shape, chair style, capacity);
     * newly reported tables are placed on the next free grid slot of their platform.
     *
     * [deleteMissing] must only be true when the server response was parsed completely.
     * A half-parsed response would otherwise delete perfectly valid local tables.
     */
    suspend fun upsertServerTables(serverTables: List<Table>, deleteMissing: Boolean = true) {
        if (serverTables.isEmpty()) return

        val existingIds = tableDao.getAllIds().toSet()
        val nextSlot = mutableMapOf<Int, Int>()

        val prepared = serverTables.map { table ->
            if (table.id in existingIds) {
                table
            } else {
                val slot = nextSlot.getOrPut(table.platformId) { tableDao.countByPlatform(table.platformId) }
                nextSlot[table.platformId] = slot + 1
                val (x, y) = gridPosition(slot)
                table.copy(positionX = x, positionY = y)
            }
        }

        tableDao.upsertFromServer(prepared, deleteMissing)
    }

    /** Merges the server's platform list without dropping the tables that reference it. */
    suspend fun upsertServerPlatforms(serverPlatforms: List<Platform>, deleteMissing: Boolean = true) {
        if (serverPlatforms.isEmpty()) return
        platformDao.upsertFromServer(serverPlatforms, deleteMissing)
    }

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

    private fun gridPosition(slot: Int): Pair<Float, Float> {
        val row = slot / GRID_COLUMNS
        val col = slot % GRID_COLUMNS
        return Pair(GRID_START_X + (col * GRID_SPACING_X), GRID_START_Y + (row * GRID_SPACING_Y))
    }

    private fun generateTables(platformId: Int, startNumber: Int, count: Int): List<Table> {
        val tables = mutableListOf<Table>()

        for (i in 0 until count) {
            val capacity = if (i % 3 == 0) 4 else 2
            val tableNumber = startNumber + i
            val (x, y) = gridPosition(i)

            tables.add(
                Table(
                    id = tableNumber,
                    name = "Tisch $tableNumber",
                    number = tableNumber,
                    capacity = capacity,
                    positionX = x,
                    positionY = y,
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

    companion object {
        private const val GRID_COLUMNS = 5
        private const val GRID_START_X = 80f
        private const val GRID_START_Y = 80f
        private const val GRID_SPACING_X = 180f
        private const val GRID_SPACING_Y = 160f
    }
}
