package com.lotus.lptablelook.network

import android.content.Context
import android.util.Log
import com.lotus.lptablelook.data.TableRepository
import com.lotus.lptablelook.model.OrderItem
import com.lotus.lptablelook.model.Platform
import com.lotus.lptablelook.model.Table
import com.lotus.lptablelook.utils.DeviceUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class SyncService(
    private val context: Context,
    private val repository: TableRepository
) {
    companion object {
        private const val TAG = "SyncService"

        /**
         * Each sum request opens its own TCP connection, so a platform with N occupied
         * tables used to cost N sequential round trips. They now run in parallel, but
         * capped: the POS server handles one connection per request and should not be
         * flooded.
         */
        private const val MAX_PARALLEL_SUM_REQUESTS = 4
    }

    private data class TableStatus(
        val tableId: Int,
        val isOccupied: Boolean,
        val waiterName: String,
        val colorCode: Int
    )

    private var socketService: SocketService? = null

    sealed class SyncResult {
        data object Success : SyncResult()
        data class Error(val message: String) : SyncResult()
        data object NoConnection : SyncResult()
    }

    fun initialize(host: String, port: Int) {
        Log.d(TAG, "initialize called with host: $host, port: $port")
        socketService = SocketService(host, port)
        Log.d(TAG, "SocketService created")
    }

    suspend fun syncPlatforms(serialNumber: String = ""): SyncResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            return@withContext SyncResult.NoConnection
        }

        val socket = socketService ?: return@withContext SyncResult.Error("Socket nicht initialisiert")

        val message = ServerCommand.CMD_GET_PLATFORMS.toMessage(serialNumber)

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                try {
                    parsePlatformsAndSave(result.data)
                    SyncResult.Success
                } catch (e: Exception) {
                    SyncResult.Error("Parse Fehler: ${e.message}")
                }
            }
            is SocketService.SocketResult.Error -> {
                SyncResult.Error(result.message)
            }
        }
    }

    suspend fun syncTables(serialNumber: String = ""): SyncResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            return@withContext SyncResult.NoConnection
        }

        val socket = socketService ?: return@withContext SyncResult.Error("Socket nicht initialisiert")

        val message = ServerCommand.CMD_GET_ALL_TABLES.toMessage(serialNumber)

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                try {
                    parseTablesAndSave(result.data)
                    SyncResult.Success
                } catch (e: Exception) {
                    SyncResult.Error("Parse Fehler: ${e.message}")
                }
            }
            is SocketService.SocketResult.Error -> {
                SyncResult.Error(result.message)
            }
        }
    }

    suspend fun syncAll(serialNumber: String = ""): SyncResult {
        // Step 1: Sync platforms
        val platformResult = syncPlatforms(serialNumber)
        if (platformResult is SyncResult.Error || platformResult is SyncResult.NoConnection) {
            return platformResult
        }

        // Step 2: Sync all tables using CMD 39 (gets table names and creates them)
        val tablesResult = syncTables(serialNumber)
        if (tablesResult is SyncResult.Error) {
            Log.e(TAG, "Error syncing tables with CMD 39")
        }

        // Step 3: Get table statuses using CMD 28 for each platform
        val platforms = repository.getAllPlatformsSync()
        Log.d(TAG, "Updating table statuses for ${platforms.size} platforms")

        for (platform in platforms) {
            Log.d(TAG, "Updating table status for platform ${platform.id}: ${platform.name}")
            val tableResult = syncFullTables(serialNumber, platform.id)
            if (tableResult is SyncResult.Error) {
                Log.e(TAG, "Error updating table status for platform ${platform.id}")
                // Continue with other platforms even if one fails
            }
        }

        return SyncResult.Success
    }

    /**
     * Fetches full table status from server (Command 28)
     * Format: 28;!;deviceId;!;platformId;!;userId;!;0
     * Response: tableId;!;tableName;!;kid;!;gameNo;!;waiterName;!;colorCode;SON;
     */
    suspend fun syncFullTables(serialNumber: String = "", platformId: Int = 0): SyncResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            return@withContext SyncResult.NoConnection
        }

        val socket = socketService ?: return@withContext SyncResult.Error("Socket nicht initialisiert")

        val deviceId = if (serialNumber.isEmpty()) getDeviceId() else serialNumber
        // Command: 28;!;deviceId;!;platformId;!;userId;!;0
        val message = ServerCommand.CMD_GET_FULL_TABLES.toMessage(deviceId, platformId.toString(), "2", "0")
        Log.d(TAG, "Requesting full tables: $message")

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                try {
                    Log.d(TAG, "Full tables response: ${result.data}")
                    parseFullTablesAndUpdate(result.data, platformId)
                    SyncResult.Success
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    SyncResult.Error("Parse Fehler: ${e.message}")
                }
            }
            is SocketService.SocketResult.Error -> {
                Log.e(TAG, "Full tables error: ${result.message}")
                SyncResult.Error(result.message)
            }
        }
    }

    /**
     * Parse CMD 28 response and UPDATE only status fields of existing tables
     * Does NOT create new tables - tables should already exist from CMD 39
     * Also fetches totalSum for occupied tables
     */
    private suspend fun parseFullTablesAndUpdate(data: String, platformId: Int = 0) {
        Log.d(TAG, "parseFullTablesAndUpdate called with data length: ${data.length}, platformId: $platformId")
        if (data.isEmpty() || data == SocketService.RETURN_FAULT) {
            Log.w(TAG, "parseFullTablesAndUpdate: Empty or fault data, returning")
            return
        }

        val records = data.split(SocketService.DATA_FINISH)
            .filter { it.isNotBlank() && it != SocketService.DATA_FINISH }

        Log.d(TAG, "Updating status for ${records.size} tables in platform $platformId")

        val statuses = records.mapNotNull { record ->
            try {
                val fields = record.split(SocketService.DATA_SEPARATOR)
                if (fields.size >= 3) {
                    // Format: tableId;!;tableName;!;kid;!;gameNo;!;waiterName;!;colorCode
                    val tableId = fields[0].toIntOrNull() ?: return@mapNotNull null
                    val kid = fields[2].toIntOrNull() ?: 0
                    TableStatus(
                        tableId = tableId,
                        isOccupied = kid > 0,
                        waiterName = if (fields.size > 4) fields[4] else "",
                        colorCode = if (fields.size > 5) fields[5].toIntOrNull() ?: 0 else 0
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing table record: $record", e)
                null
            }
        }

        // Free tables need no network round trip at all.
        statuses.filterNot { it.isOccupied }.forEach { status ->
            repository.updateTableStatusWithSum(status.tableId, false, status.waiterName, status.colorCode, 0.0)
        }

        val occupied = statuses.filter { it.isOccupied }
        if (occupied.isEmpty()) return

        Log.d(TAG, "Fetching sums for ${occupied.size} occupied tables (max $MAX_PARALLEL_SUM_REQUESTS in parallel)")

        val sums = coroutineScope {
            val gate = Semaphore(MAX_PARALLEL_SUM_REQUESTS)
            occupied.map { status ->
                async {
                    gate.withPermit {
                        status.tableId to fetchTableSum(status.tableId).getOrElse { 0.0 }
                    }
                }
            }.awaitAll()
        }.toMap()

        for (status in occupied) {
            val totalSum = sums[status.tableId] ?: 0.0
            repository.updateTableStatusWithSum(status.tableId, true, status.waiterName, status.colorCode, totalSum)
            Log.d(TAG, "Updated table ${status.tableId}: waiter=${status.waiterName}, color=${status.colorCode}, sum=$totalSum")
        }
    }

    /**
     * Fetch table sum by getting orders and calculating total (internal use during sync)
     * CMD 27 doesn't return sum directly, so we use CMD 32 to get orders and calculate
     */
    private suspend fun fetchTableSum(tableId: Int): Result<Double> {
        val socket = socketService ?: run {
            Log.e(TAG, "fetchTableSum: Socket not initialized for table $tableId")
            return Result.failure(Exception("Socket nicht initialisiert"))
        }

        val deviceId = getDeviceId()
        // Use CMD 32 to get table orders
        val message = ServerCommand.CMD_GET_TABLE_ORDERS.toMessage(deviceId, tableId.toString())
        Log.d(TAG, "fetchTableSum: Requesting orders for table $tableId to calculate sum, message: $message")

        return when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                Log.d(TAG, "fetchTableSum: Got orders response for table $tableId, length: ${result.data.length}")
                try {
                    // Parse orders and calculate sum
                    val orders = parseTableOrders(result.data)
                    val totalSum = orders.sumOf { it.total - it.discount }
                    Log.d(TAG, "fetchTableSum: Calculated sum for table $tableId: $totalSum (${orders.size} orders)")
                    Result.success(totalSum)
                } catch (e: Exception) {
                    Log.e(TAG, "fetchTableSum: Parse error for table $tableId: ${e.message}")
                    Result.success(0.0)
                }
            }
            is SocketService.SocketResult.Error -> {
                Log.e(TAG, "fetchTableSum: Error for table $tableId: ${result.message}")
                Result.success(0.0) // Return 0 on error to not break sync
            }
        }
    }

    suspend fun testConnection(): SyncResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            Log.d(TAG, "No WiFi connection")
            return@withContext SyncResult.NoConnection
        }

        val socket = socketService
        if (socket == null) {
            Log.e(TAG, "SocketService is null!")
            return@withContext SyncResult.Error("Socket nicht initialisiert")
        }

        Log.d(TAG, "Testing connection...")

        // Just test if we can connect by sending register command
        val deviceId = getDeviceId()
        val message = ServerCommand.CMD_REGISTER_PHONE.toMessage(deviceId)
        Log.d(TAG, "Sending register message: $message")

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                Log.d(TAG, "Connection test success: ${result.data}")
                SyncResult.Success
            }
            is SocketService.SocketResult.Error -> {
                Log.e(TAG, "Connection test error: ${result.message}")
                SyncResult.Error(result.message)
            }
        }
    }

    fun getDeviceId(): String = DeviceUtils.getDeviceId(context)

    suspend fun registerDevice(): SyncResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            return@withContext SyncResult.NoConnection
        }

        val socket = socketService ?: return@withContext SyncResult.Error("Socket nicht initialisiert")

        val deviceId = getDeviceId()
        val message = ServerCommand.CMD_REGISTER_PHONE.toMessage(deviceId)

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                if (result.data.contains(SocketService.RETURN_OK)) {
                    SyncResult.Success
                } else {
                    SyncResult.Error("Registrierung fehlgeschlagen")
                }
            }
            is SocketService.SocketResult.Error -> SyncResult.Error(result.message)
        }
    }

    suspend fun testConnectionAndRegister(): SyncResult {
        // testConnection now also registers the device
        return testConnection()
    }

    data class TableStatusResult(
        val tableId: Int,
        val isOccupied: Boolean,
        val waiterName: String = "",
        val totalAmount: Double = 0.0,
        val orderCount: Int = 0
    )

    suspend fun getTableStatus(tableId: Int): Result<TableStatusResult> = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(Exception("Keine WiFi-Verbindung"))
        }

        val socket = socketService ?: return@withContext Result.failure(Exception("Socket nicht initialisiert"))

        val message = ServerCommand.CMD_TABLE_STATUS.toMessage(tableId.toString())
        Log.d(TAG, "Requesting table status: $message")

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                Log.d(TAG, "Table status response: ${result.data}")
                try {
                    val statusResult = parseTableStatus(tableId, result.data)
                    Result.success(statusResult)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    // Return default status if parsing fails
                    Result.success(TableStatusResult(tableId, false))
                }
            }
            is SocketService.SocketResult.Error -> {
                Log.e(TAG, "Table status error: ${result.message}")
                Result.failure(Exception(result.message))
            }
        }
    }

    private fun parseTableStatus(tableId: Int, data: String): TableStatusResult {
        // Parse server response - format may vary based on server implementation
        // Default: return basic status
        val isOccupied = data.contains("1") || data.contains(SocketService.RETURN_OK)
        return TableStatusResult(
            tableId = tableId,
            isOccupied = isOccupied
        )
    }

    private suspend fun parsePlatformsAndSave(data: String) {
        if (data.isEmpty() || data == SocketService.RETURN_FAULT) {
            return
        }

        val records = data.split(SocketService.DATA_FINISH)
            .filter { it.isNotBlank() && it != SocketService.DATA_FINISH }

        val platforms = records.mapNotNull { record ->
            try {
                val fields = record.split(SocketService.DATA_SEPARATOR)
                if (fields.size >= 3) {
                    Platform(
                        id = fields[0].toInt(),
                        name = fields[2] // fields[1] = sira, fields[2] = aciklama/name
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        if (platforms.isNotEmpty()) {
            // Merge instead of delete+insert: dropping the platform rows would leave
            // the tables that reference them orphaned and invisible in every tab.
            // Only prune locally known platforms when every record could be parsed.
            val complete = platforms.size == records.size
            if (!complete) {
                Log.w(TAG, "parsePlatformsAndSave: ${records.size - platforms.size} record(s) unparsed, skipping prune")
            }
            repository.upsertServerPlatforms(platforms, deleteMissing = complete)
        } else {
            Log.w(TAG, "parsePlatformsAndSave: no platform parsed, keeping local data")
        }
    }

    private suspend fun parseTablesAndSave(data: String) {
        if (data.isEmpty() || data == SocketService.RETURN_FAULT) {
            return
        }

        val records = data.split(SocketService.DATA_FINISH)
            .filter { it.isNotBlank() && it != SocketService.DATA_FINISH }

        val tables = records.mapNotNull { record ->
            try {
                val fields = record.split(SocketService.DATA_SEPARATOR)
                if (fields.size >= 3) {
                    // Server format: id;!;pltId;!;masaAdi
                    val id = fields[0].toIntOrNull() ?: return@mapNotNull null
                    val platformId = fields[1].toIntOrNull() ?: 1
                    val name = fields[2]

                    // Layout fields stay at their defaults here on purpose: the repository
                    // keeps the stored position/size/shape for tables we already know and
                    // only assigns a grid slot to genuinely new ones.
                    Table(
                        id = id,
                        name = name,
                        number = id,
                        platformId = platformId
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }

        if (tables.isNotEmpty()) {
            // Merge instead of delete+insert, otherwise every sync would wipe the
            // drag & drop layout and the per-table appearance the user configured.
            // Only prune locally known tables when every record could be parsed.
            val complete = tables.size == records.size
            if (!complete) {
                Log.w(TAG, "parseTablesAndSave: ${records.size - tables.size} record(s) unparsed, skipping prune")
            }
            repository.upsertServerTables(tables, deleteMissing = complete)
        } else {
            Log.w(TAG, "parseTablesAndSave: no table parsed, keeping local data")
        }
    }

    /**
     * Get table orders from server (Command 32)
     * Format: 32;!;deviceId;!;tableId
     */
    suspend fun getTableOrders(tableId: Int): Result<List<OrderItem>> = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isWifiConnected(context)) {
            return@withContext Result.failure(Exception("Keine WiFi-Verbindung"))
        }

        val socket = socketService ?: return@withContext Result.failure(Exception("Socket nicht initialisiert"))

        val deviceId = getDeviceId()
        val message = ServerCommand.CMD_GET_TABLE_ORDERS.toMessage(deviceId, tableId.toString())
        Log.d(TAG, "Requesting table orders: $message")

        when (val result = socket.sendMessage(message)) {
            is SocketService.SocketResult.Success -> {
                Log.d(TAG, "Table orders response: ${result.data}")
                try {
                    val orders = parseTableOrders(result.data)
                    Result.success(orders)
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    Result.success(emptyList())
                }
            }
            is SocketService.SocketResult.Error -> {
                Log.e(TAG, "Table orders error: ${result.message}")
                Result.failure(Exception(result.message))
            }
        }
    }

    private fun parseTableOrders(data: String): List<OrderItem> {
        Log.d(TAG, "parseTableOrders raw data length: ${data.length}")
        if (data.isEmpty() || data.length < 10 || data == SocketService.RETURN_FAULT) {
            Log.d(TAG, "parseTableOrders: empty or fault data")
            return emptyList()
        }

        val orders = mutableListOf<OrderItem>()
        val records = data.split(SocketService.DATA_FINISH)
            .filter { it.isNotBlank() && it != SocketService.DATA_FINISH }

        Log.d(TAG, "parseTableOrders: ${records.size} records found")

        for (record in records) {
            try {
                val fields = record.split(SocketService.DATA_SEPARATOR)
                Log.d(TAG, "parseTableOrders record fields count: ${fields.size}")
                if (fields.size >= 12) {
                    val type = fields[0].toIntOrNull() ?: 0
                    val productCode = fields.getOrNull(4) ?: ""
                    val productName = fields.getOrNull(5) ?: ""
                    val quantity = fields.getOrNull(8)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    val unitPrice = fields.getOrNull(9)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    val total = fields.getOrNull(10)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    val discount = fields.getOrNull(11)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
                    val id = fields.getOrNull(20)?.toIntOrNull() ?: 0

                    Log.d(TAG, "Parsed order: $productName, qty=$quantity, total=$total, discount=$discount")

                    if (type >= 0 && productName.isNotEmpty()) {
                        orders.add(
                            OrderItem(
                                id = id,
                                productCode = productCode,
                                productName = productName,
                                quantity = quantity,
                                unitPrice = unitPrice,
                                total = total,
                                discount = discount,
                                type = type
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing order: $record", e)
            }
        }

        Log.d(TAG, "parseTableOrders: returning ${orders.size} orders")
        return orders
    }
}
