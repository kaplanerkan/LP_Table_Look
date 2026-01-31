package com.lotus.lptablelook

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.lotus.lptablelook.data.TableRepository
import com.lotus.lptablelook.model.FIXED_PORT
import com.lotus.lptablelook.model.Platform
import com.lotus.lptablelook.network.SyncService
import com.lotus.lptablelook.ui.EditTableDialog
import com.lotus.lptablelook.ui.PopupMessage
import com.lotus.lptablelook.ui.ProgressDialog
import com.lotus.lptablelook.ui.TableDetailsDialog
import com.lotus.lptablelook.ui.TableOrdersDialog
import com.lotus.lptablelook.ui.WifiInfoDialog
import com.lotus.lptablelook.ui.BatteryInfoDialog
import com.lotus.lptablelook.view.TableFloorView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var tableFloorView: TableFloorView
    private lateinit var platformContainer: LinearLayout
    private lateinit var switchEditMode: SwitchCompat
    private lateinit var editModeBanner: TextView
    private lateinit var btnScaleUp: ImageButton
    private lateinit var btnScaleDown: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var ivWifiStatus: ImageView
    private lateinit var ivBatteryStatus: ImageView
    private lateinit var tvBatteryPercent: TextView

    private lateinit var repository: TableRepository
    private lateinit var syncService: SyncService
    private val platforms = mutableListOf<Platform>()
    private var currentPlatform: Platform? = null
    private var currentScale = 1.0f
    private var currentSocketIp: String = ""

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full screen immersive mode
        enableFullScreen()

        setContentView(R.layout.activity_main)

        val app = application as TableLookApp
        repository = app.repository
        syncService = app.syncService
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        initViews()
        setupListeners()
        loadData()
        loadSettings()
        setupWifiMonitor()
        setupBatteryMonitor()
    }

    private fun enableFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupWifiMonitor() {
        // Check initial state
        updateWifiStatus()

        // Register network callback for real-time updates
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { updateWifiStatus() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { updateWifiStatus() }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                runOnUiThread { updateWifiStatus() }
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    private fun updateWifiStatus() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val isWifiEnabled = wifiManager.isWifiEnabled

        if (isWifiEnabled && isWifiConnected()) {
            // Get signal strength using new API (Android 11+)
            val signalLevel = getWifiSignalLevel()

            // WiFi connected - show signal strength with appropriate icon and color
            val (iconRes, color) = when (signalLevel) {
                4 -> Pair(R.drawable.ic_wifi_4, getColor(R.color.table_available))
                3 -> Pair(R.drawable.ic_wifi_3, getColor(R.color.table_available))
                2 -> Pair(R.drawable.ic_wifi_2, getColor(R.color.table_occupied_orange))
                1 -> Pair(R.drawable.ic_wifi_1, getColor(R.color.table_occupied_orange))
                else -> Pair(R.drawable.ic_wifi_1, getColor(R.color.table_occupied))
            }
            ivWifiStatus.setImageResource(iconRes)
            ivWifiStatus.setColorFilter(color)
        } else {
            // WiFi not connected - show off icon in red
            ivWifiStatus.setImageResource(R.drawable.ic_wifi_off)
            ivWifiStatus.setColorFilter(getColor(R.color.table_occupied))
        }
    }

    private fun getWifiSignalLevel(): Int {
        val network = connectivityManager.activeNetwork ?: return 0
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return 0

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return 0

        // Use signalStrength from NetworkCapabilities (returns value from 0-100 or RSSI)
        val signalStrength = caps.signalStrength

        // Convert signal strength to 0-4 levels
        // signalStrength is typically RSSI value (-100 to 0 dBm) or 0-100 percentage
        return when {
            signalStrength >= -50 || signalStrength >= 80 -> 4  // Excellent
            signalStrength >= -60 || signalStrength >= 60 -> 3  // Good
            signalStrength >= -70 || signalStrength >= 40 -> 2  // Fair
            signalStrength >= -80 || signalStrength >= 20 -> 1  // Weak
            else -> 0  // Very weak
        }
    }

    private fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun setupBatteryMonitor() {
        // Get initial battery status
        updateBatteryStatus()

        // Register battery changed receiver
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, batteryFilter)
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { updateBatteryStatus(it) }
        }
    }

    private fun updateBatteryStatus(intent: Intent? = null) {
        val batteryIntent = intent ?: IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            registerReceiver(null, filter)
        }

        batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // Update percentage text
            tvBatteryPercent.text = "$batteryPct%"

            // Choose icon and color based on charging status and level
            val (iconRes, color) = when {
                isCharging -> Pair(R.drawable.ic_battery_charging, getColor(R.color.table_available))
                batteryPct >= 80 -> Pair(R.drawable.ic_battery_full, getColor(R.color.table_available))
                batteryPct >= 50 -> Pair(R.drawable.ic_battery_80, getColor(R.color.table_available))
                batteryPct >= 20 -> Pair(R.drawable.ic_battery_50, getColor(R.color.table_occupied_orange))
                batteryPct >= 10 -> Pair(R.drawable.ic_battery_20, getColor(R.color.table_occupied_orange))
                else -> Pair(R.drawable.ic_battery_alert, getColor(R.color.table_occupied))
            }

            ivBatteryStatus.setImageResource(iconRes)
            ivBatteryStatus.setColorFilter(color)
            tvBatteryPercent.setTextColor(color)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val settings = repository.getSettingsSync()
            android.util.Log.d("MainActivity", "loadSettings - loaded: $settings")
            settings?.let {
                currentSocketIp = it.socketIp
                android.util.Log.d("MainActivity", "loadSettings - IP: $currentSocketIp")
                if (currentSocketIp.isNotEmpty()) {
                    syncService.initialize(currentSocketIp, FIXED_PORT)
                }
                // Set background style
                tableFloorView.setBackgroundStyle(it.backgroundStyle)
                // Set show chairs setting
                tableFloorView.setShowChairs(it.showChairs)
                // Set show prices setting
                tableFloorView.setShowPrices(it.showPrices)
                // Set show currency symbol setting
                tableFloorView.setShowCurrencySymbol(it.showCurrencySymbol)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload settings when returning from SettingsActivity
        loadSettings()
        // Reload floor plan background (may have changed in Settings)
        tableFloorView.loadBackgroundImage()
    }

    private fun refreshTableStatuses() {
        lifecycleScope.launch {
            // Reload settings if IP is empty
            if (currentSocketIp.isEmpty()) {
                val settings = repository.getSettingsSync()
                settings?.let {
                    currentSocketIp = it.socketIp
                    if (currentSocketIp.isNotEmpty()) {
                        syncService.initialize(currentSocketIp, FIXED_PORT)
                    }
                }
            }

            if (currentSocketIp.isEmpty()) {
                PopupMessage.warning(this@MainActivity, getString(R.string.configure_server_first)).show()
                return@launch
            }

            doRefreshTables()
        }
    }

    private fun doRefreshTables() {
        if (currentSocketIp.isEmpty()) return

        val progressDialog = ProgressDialog(this).apply {
            show(getString(R.string.updating), getString(R.string.retrieving_table_status))
        }

        lifecycleScope.launch {
            val platformId = currentPlatform?.id ?: 0

            when (val result = syncService.syncFullTables(platformId = platformId)) {
                is SyncService.SyncResult.Success -> {
                    progressDialog.dismiss()
                    // Reload tables for current platform
                    currentPlatform?.let { platform ->
                        val tables = repository.getTablesByPlatformSync(platform.id)
                        android.util.Log.d("MainActivity", "Reloaded ${tables.size} tables after sync")
                        tables.filter { it.isOccupied }.forEach {
                            android.util.Log.d("MainActivity", "  Table ${it.name}: totalSum=${it.totalSum}")
                        }
                        platform.tables.clear()
                        platform.tables.addAll(tables)
                        runOnUiThread {
                            tableFloorView.setTables(platform.tables)
                            tableFloorView.invalidate()
                        }
                    }
                    PopupMessage.success(this@MainActivity, getString(R.string.table_status_updated)).show()
                }
                is SyncService.SyncResult.Error -> {
                    progressDialog.dismiss()
                    PopupMessage.error(this@MainActivity, getString(R.string.error_prefix, result.message)).show()
                }
                is SyncService.SyncResult.NoConnection -> {
                    progressDialog.dismiss()
                    PopupMessage.error(this@MainActivity, getString(R.string.no_wifi_connection)).show()
                }
            }
        }
    }

    private fun initViews() {
        tableFloorView = findViewById(R.id.tableFloorView)
        platformContainer = findViewById(R.id.platformContainer)
        switchEditMode = findViewById(R.id.switchEditMode)
        editModeBanner = findViewById(R.id.editModeBanner)
        btnScaleUp = findViewById(R.id.btnScaleUp)
        btnScaleDown = findViewById(R.id.btnScaleDown)
        btnSettings = findViewById(R.id.btnSettings)
        btnRefresh = findViewById(R.id.btnRefresh)
        ivWifiStatus = findViewById(R.id.ivWifiStatus)
        ivBatteryStatus = findViewById(R.id.ivBatteryStatus)
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent)

        // Load floor plan background image
        tableFloorView.loadBackgroundImage()
    }

    private fun loadData() {
        lifecycleScope.launch {
            // Initialize default data if empty
            repository.initializeDefaultData()

            // Load platforms
            repository.getAllPlatforms().collect { platformList ->
                platforms.clear()
                platforms.addAll(platformList)

                // Load tables for each platform
                for (platform in platforms) {
                    val tables = repository.getTablesByPlatformSync(platform.id)
                    platform.tables.clear()
                    platform.tables.addAll(tables)
                }

                createPlatformTabs()

                if (currentPlatform == null && platforms.isNotEmpty()) {
                    selectPlatform(platforms.first())
                }
            }
        }
    }

    private fun createPlatformTabs() {
        platformContainer.removeAllViews()

        for (platform in platforms) {
            val tabView = createTabView(platform)
            platformContainer.addView(tabView)
        }
    }

    private fun createTabView(platform: Platform): TextView {
        val tabTextSize = resources.getDimension(R.dimen.platform_tab_text_size) / (resources.displayMetrics.density * resources.configuration.fontScale)
        val tabPaddingH = resources.getDimensionPixelSize(R.dimen.platform_tab_padding_horizontal)
        val tabPaddingV = resources.getDimensionPixelSize(R.dimen.platform_tab_padding_vertical)

        return TextView(this).apply {
            text = platform.name
            textSize = tabTextSize
            setTextColor(getColor(R.color.tab_text))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(tabPaddingH, tabPaddingV, tabPaddingH, tabPaddingV)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            params.marginEnd = 4
            layoutParams = params

            background = createTabBackground(false)

            setOnClickListener {
                selectPlatform(platform)
            }

            tag = platform.id
        }
    }

    private fun createTabBackground(isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(12f, 12f, 12f, 12f, 0f, 0f, 0f, 0f)
            setColor(
                if (isSelected) getColor(R.color.tab_selected)
                else getColor(R.color.tab_unselected)
            )
        }
    }

    private fun selectPlatform(platform: Platform) {
        currentPlatform = platform

        for (i in 0 until platformContainer.childCount) {
            val tabView = platformContainer.getChildAt(i) as TextView
            val isSelected = tabView.tag == platform.id
            tabView.background = createTabBackground(isSelected)
        }

        tableFloorView.setTables(platform.tables)
    }

    private fun setupListeners() {
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnRefresh.setOnClickListener {
            refreshTableStatuses()
        }

        ivWifiStatus.setOnClickListener {
            WifiInfoDialog(this).show()
        }

        ivBatteryStatus.setOnClickListener {
            BatteryInfoDialog(this).show()
        }

        tvBatteryPercent.setOnClickListener {
            BatteryInfoDialog(this).show()
        }

        switchEditMode.setOnCheckedChangeListener { _, isChecked ->
            tableFloorView.setEditMode(isChecked)
            editModeBanner.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnScaleUp.setOnClickListener {
            if (currentScale < 2.0f) {
                currentScale += 0.1f
                tableFloorView.tableScale = currentScale
            }
        }

        btnScaleDown.setOnClickListener {
            if (currentScale > 0.5f) {
                currentScale -= 0.1f
                tableFloorView.tableScale = currentScale
            }
        }

        tableFloorView.onTableClicked = { table ->
            if (!tableFloorView.isEditModeEnabled()) {
                onTableClicked(table)
            }
        }

        tableFloorView.onTablePositionChanged = { table ->
            // Save position to database
            lifecycleScope.launch {
                repository.updateTablePosition(table.id, table.positionX, table.positionY)
            }
        }

        tableFloorView.onTableLongClicked = { table ->
            // Show edit dialog only in edit mode
            if (tableFloorView.isEditModeEnabled()) {
                showEditTableDialog(table)
            }
        }
    }

    private fun showEditTableDialog(table: com.lotus.lptablelook.model.Table) {
        EditTableDialog(this, table) { isOval, capacity, width, height, chairStyle ->
            // Update table properties
            table.isOval = isOval
            table.capacity = capacity
            table.width = width
            table.height = height
            table.chairStyle = chairStyle

            // Refresh view
            tableFloorView.invalidate()

            // Save to database
            lifecycleScope.launch {
                repository.updateTableAppearance(table.id, isOval, capacity, width, height, chairStyle)
            }

            PopupMessage.success(this, getString(R.string.table_updated)).show()
        }.show()
    }

    private fun onTableClicked(table: com.lotus.lptablelook.model.Table) {
         android.util.Log.d("MainActivity", "onTableClicked: table=${table.name}, id=${table.id}")
        // Directly load and show table orders
        loadTableOrders(table)
    }

    private fun showTableDetailsDialog(table: com.lotus.lptablelook.model.Table, platformName: String) {
        TableDetailsDialog(this, table, platformName)
            .setOnStatusChangeListener { clickedTable ->
                // Toggle status
                clickedTable.isOccupied = !clickedTable.isOccupied
                tableFloorView.invalidate()

                // Save to database
                lifecycleScope.launch {
                    repository.updateTableOccupied(clickedTable.id, clickedTable.isOccupied)
                }

                PopupMessage.info(
                    this,
                    getString(if (clickedTable.isOccupied) R.string.table_marked_occupied else R.string.table_marked_available)
                ).show()
            }
            .setOnDetailsListener { clickedTable ->
                loadTableOrders(clickedTable)
            }
            .show()
    }

    private fun loadTableOrders(table: com.lotus.lptablelook.model.Table) {
        if (currentSocketIp.isEmpty()) {
            PopupMessage.warning(this, getString(R.string.configure_server_first)).show()
            return
        }

        val tableName = if (table.name.isNotEmpty()) table.name else getString(R.string.table_number, table.number)
        val progressDialog = ProgressDialog(this).apply {
            show(getString(R.string.checking_connection), getString(R.string.contacting_register))
        }

        lifecycleScope.launch {
            // First test connection
            val connectionResult = syncService.testConnectionAndRegister()

            when (connectionResult) {
                is SyncService.SyncResult.NoConnection -> {
                    progressDialog.dismiss()
                    PopupMessage.error(this@MainActivity, getString(R.string.no_wifi_available)).show()
                    return@launch
                }
                is SyncService.SyncResult.Error -> {
                    progressDialog.dismiss()
                    PopupMessage.error(this@MainActivity, getString(R.string.register_not_reachable)).show()
                    return@launch
                }
                is SyncService.SyncResult.Success -> {
                    // Connection OK, proceed with loading orders
                    progressDialog.updateMessage(getString(R.string.loading_table, tableName))
                }
            }

            // Get orders and sum
            val ordersResult = syncService.getTableOrders(table.id)
            val sumResult = syncService.getTableSum(table.id)

            progressDialog.dismiss()

            // Check if orders request failed
            if (ordersResult.isFailure) {
                PopupMessage.error(this@MainActivity, getString(R.string.orders_load_error)).show()
                return@launch
            }

            val orders = ordersResult.getOrElse { emptyList() }
            android.util.Log.d("MainActivity", "Orders count: ${orders.size}")

            // Get server sum or calculate from orders
            val serverSum = sumResult.getOrElse { 0.0 }
            android.util.Log.d("MainActivity", "Server sum: $serverSum")

            // Calculate sum from orders
            val calculatedSum = orders.sumOf { it.total - it.discount }
            android.util.Log.d("MainActivity", "Calculated sum: $calculatedSum")

            // Use calculated sum if server sum is 0 or invalid
            val totalSum = if (serverSum > 0) serverSum else calculatedSum
            android.util.Log.d("MainActivity", "Final totalSum: $totalSum")

            TableOrdersDialog(this@MainActivity, table, orders, totalSum).show()
        }
    }
}
