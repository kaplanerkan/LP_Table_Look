package com.lotus.lptablelook

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.lotus.lptablelook.ui.PopupMessage
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.lotus.lptablelook.data.TableRepository
import com.lotus.lptablelook.model.FIXED_PORT
import com.lotus.lptablelook.model.Settings
import com.lotus.lptablelook.network.SyncService
import com.lotus.lptablelook.ui.ProgressDialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val FLOOR_PLAN_FILENAME = "floor_plan.png"
        private const val MAX_FILE_SIZE = 1024 * 1024 // 1 MB
    }

    private lateinit var btnBack: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var etSocketIp: TextInputEditText
    private lateinit var etSocketPort: TextInputEditText
    private lateinit var switchAutoConnect: SwitchCompat
    private lateinit var etRestaurantName: TextInputEditText
    private lateinit var seekBarTableScale: SeekBar
    private lateinit var tvScaleValue: TextView
    private lateinit var btnTestConnection: MaterialButton
    private lateinit var btnSyncData: MaterialButton
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvDeviceId: TextView

    // Floor plan background
    private lateinit var ivFloorPlanPreview: ImageView
    private lateinit var tvNoFloorPlan: TextView
    private lateinit var btnSelectFloorPlan: MaterialButton
    private lateinit var btnRemoveFloorPlan: MaterialButton

    // Background style buttons
    private lateinit var btnBgBlue: ImageButton
    private lateinit var btnBgWarm: ImageButton
    private lateinit var btnBgDark: ImageButton
    private lateinit var btnBgGreen: ImageButton
    private lateinit var btnBgPurple: ImageButton
    private lateinit var bgStyleButtons: List<ImageButton>
    private var currentBackgroundStyle: Int = 0

    // Show chairs toggle
    private lateinit var switchShowChairs: SwitchCompat
    private lateinit var switchShowPrices: SwitchCompat
    private lateinit var switchShowCurrencySymbol: SwitchCompat

    private lateinit var repository: TableRepository
    private lateinit var syncService: SyncService
    private var currentSettings: Settings? = null

    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val app = application as TableLookApp
        repository = app.repository
        syncService = app.syncService

        initViews()
        setupListeners()
        loadSettings()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        etSocketIp = findViewById(R.id.etSocketIp)
        etSocketPort = findViewById(R.id.etSocketPort)
        switchAutoConnect = findViewById(R.id.switchAutoConnect)
        etRestaurantName = findViewById(R.id.etRestaurantName)
        seekBarTableScale = findViewById(R.id.seekBarTableScale)
        tvScaleValue = findViewById(R.id.tvScaleValue)
        btnTestConnection = findViewById(R.id.btnTestConnection)
        btnSyncData = findViewById(R.id.btnSyncData)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvDeviceId = findViewById(R.id.tvDeviceId)

        // Port is fixed at 1453
        etSocketPort.setText(FIXED_PORT.toString())
        etSocketPort.isEnabled = false

        // Sync button disabled by default - enabled after successful connection test
        btnSyncData.isEnabled = false

        // Show device ID
        tvDeviceId.text = syncService.getDeviceId()

        // Floor plan views
        ivFloorPlanPreview = findViewById(R.id.ivFloorPlanPreview)
        tvNoFloorPlan = findViewById(R.id.tvNoFloorPlan)
        btnSelectFloorPlan = findViewById(R.id.btnSelectFloorPlan)
        btnRemoveFloorPlan = findViewById(R.id.btnRemoveFloorPlan)

        // Load existing floor plan if any
        loadFloorPlanPreview()

        // Background style buttons
        btnBgBlue = findViewById(R.id.btnBgBlue)
        btnBgWarm = findViewById(R.id.btnBgWarm)
        btnBgDark = findViewById(R.id.btnBgDark)
        btnBgGreen = findViewById(R.id.btnBgGreen)
        btnBgPurple = findViewById(R.id.btnBgPurple)
        bgStyleButtons = listOf(btnBgBlue, btnBgWarm, btnBgDark, btnBgGreen, btnBgPurple)

        // Show chairs toggle
        switchShowChairs = findViewById(R.id.switchShowChairs)
        switchShowPrices = findViewById(R.id.switchShowPrices)
        switchShowCurrencySymbol = findViewById(R.id.switchShowCurrencySymbol)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveSettings()
        }

        btnTestConnection.setOnClickListener {
            testConnection()
        }

        btnSyncData.setOnClickListener {
            syncData()
        }

        seekBarTableScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvScaleValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSelectFloorPlan.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        btnRemoveFloorPlan.setOnClickListener {
            removeFloorPlan()
        }

        // Background style button listeners
        btnBgBlue.setOnClickListener { selectBackgroundStyle(0) }
        btnBgWarm.setOnClickListener { selectBackgroundStyle(1) }
        btnBgDark.setOnClickListener { selectBackgroundStyle(2) }
        btnBgGreen.setOnClickListener { selectBackgroundStyle(3) }
        btnBgPurple.setOnClickListener { selectBackgroundStyle(4) }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            repository.initializeSettings()

            // Load settings once (not as Flow)
            val settings = repository.getSettingsSync()
            Log.d("SettingsActivity", "loadSettings - loaded: $settings")
            settings?.let {
                currentSettings = it
                updateUI(it)
            }
        }
    }

    private fun updateUI(settings: Settings) {
        etSocketIp.setText(settings.socketIp)
        switchAutoConnect.isChecked = settings.autoConnect
        etRestaurantName.setText(settings.restaurantName)

        val scaleProgress = (settings.tableScaleDefault * 100).toInt()
        seekBarTableScale.progress = scaleProgress
        tvScaleValue.text = "$scaleProgress%"

        // Set background style
        currentBackgroundStyle = settings.backgroundStyle
        updateBackgroundStyleSelection()

        // Set show chairs toggle
        switchShowChairs.isChecked = settings.showChairs
        switchShowPrices.isChecked = settings.showPrices
        switchShowCurrencySymbol.isChecked = settings.showCurrencySymbol
    }

    private fun saveSettings() {
        val socketIp = etSocketIp.text?.toString()?.trim() ?: ""
        val autoConnect = switchAutoConnect.isChecked
        val restaurantName = etRestaurantName.text?.toString()?.trim() ?: ""
        val tableScale = seekBarTableScale.progress / 100f
        val showChairs = switchShowChairs.isChecked
        val showPrices = switchShowPrices.isChecked
        val showCurrencySymbol = switchShowCurrencySymbol.isChecked

        val settings = Settings(
            id = 1,
            socketIp = socketIp,
            socketPort = FIXED_PORT,
            autoConnect = autoConnect,
            restaurantName = restaurantName,
            tableScaleDefault = tableScale,
            backgroundStyle = currentBackgroundStyle,
            showChairs = showChairs,
            showPrices = showPrices,
            showCurrencySymbol = showCurrencySymbol
        )

        Log.d("SettingsActivity", "saveSettings - saving: $settings")

        lifecycleScope.launch {
            repository.saveSettings(settings)
            Log.d("SettingsActivity", "saveSettings - saved successfully")
            PopupMessage.success(this@SettingsActivity, getString(R.string.settings_saved)).show()
            finish()
        }
    }

    private fun testConnection() {
        val ip = etSocketIp.text?.toString()?.trim() ?: ""

        if (ip.isEmpty()) {
            showStatus(getString(R.string.please_enter_ip), false)
            return
        }

        Log.d("SettingsActivity", "testConnection called with IP: $ip, Port: $FIXED_PORT")

        setButtonsEnabled(false)
        val progressDialog = ProgressDialog.showConnectionTest(this)

        lifecycleScope.launch {
            Log.d("SettingsActivity", "Initializing syncService...")
            syncService.initialize(ip, FIXED_PORT)
            Log.d("SettingsActivity", "syncService initialized")

            progressDialog.updateMessage(getString(R.string.registering_device))

            Log.d("SettingsActivity", "Calling testConnectionAndRegister...")
            when (val result = syncService.testConnectionAndRegister()) {
                is SyncService.SyncResult.Success -> {
                    showStatus(getString(R.string.connection_successful), true)
                    // Save IP to Room on successful connection
                    saveConnectionSettings(ip)
                    // Enable Sync button only on successful connection
                    btnSyncData.isEnabled = true
                }
                is SyncService.SyncResult.Error -> {
                    showStatus(getString(R.string.error_prefix, result.message), false)
                    // Keep Sync button disabled on error
                    btnSyncData.isEnabled = false
                }
                is SyncService.SyncResult.NoConnection -> {
                    showStatus(getString(R.string.no_wifi_connection), false)
                    // Keep Sync button disabled on no connection
                    btnSyncData.isEnabled = false
                }
            }

            progressDialog.dismiss()
            btnTestConnection.isEnabled = true
        }
    }

    private suspend fun saveConnectionSettings(ip: String) {
        // Get current settings from DB if not loaded yet
        val current = currentSettings ?: repository.getSettingsSync() ?: Settings()
        val updated = current.copy(
            id = 1,  // Ensure ID is always 1
            socketIp = ip,
            socketPort = FIXED_PORT
        )
        repository.saveSettings(updated)
        currentSettings = updated

        // Also update the UI
        runOnUiThread {
            etSocketIp.setText(ip)
        }
        Log.d("SettingsActivity", "Connection settings saved - IP: $ip, Port: $FIXED_PORT")
    }

    private fun syncData() {
        val ip = etSocketIp.text?.toString()?.trim() ?: ""

        if (ip.isEmpty()) {
            showStatus(getString(R.string.please_enter_ip), false)
            return
        }

        setButtonsEnabled(false)
        val progressDialog = ProgressDialog.showSync(this)

        lifecycleScope.launch {
            syncService.initialize(ip, FIXED_PORT)

            val deviceId = syncService.getDeviceId()

            progressDialog.updateMessage(getString(R.string.loading_platforms))

            when (val result = syncService.syncAll(deviceId)) {
                is SyncService.SyncResult.Success -> {
                    showStatus(getString(R.string.sync_successful), true)
                    // Save IP to Room on successful sync
                    saveConnectionSettings(ip)
                    PopupMessage.success(this@SettingsActivity, getString(R.string.data_updated)).show()
                }
                is SyncService.SyncResult.Error -> {
                    showStatus(getString(R.string.error_prefix, result.message), false)
                }
                is SyncService.SyncResult.NoConnection -> {
                    showStatus(getString(R.string.no_wifi_connection), false)
                }
            }

            progressDialog.dismiss()
            setButtonsEnabled(true)
        }
    }

    private fun showStatus(message: String, success: Boolean?) {
        tvConnectionStatus.visibility = View.VISIBLE
        tvConnectionStatus.text = message
        tvConnectionStatus.setTextColor(
            getColor(
                when (success) {
                    true -> R.color.table_available
                    false -> R.color.table_occupied
                    null -> R.color.primary
                }
            )
        )
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnTestConnection.isEnabled = enabled
        btnSyncData.isEnabled = enabled
    }

    private fun handleSelectedImage(uri: Uri) {
        try {
            // Check file size
            val inputStream = contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes == null) {
                PopupMessage.error(this, getString(R.string.image_read_error)).show()
                return
            }

            if (bytes.size > MAX_FILE_SIZE) {
                PopupMessage.warning(
                    this,
                    getString(R.string.file_too_large)
                ).show()
                return
            }

            // Save to internal storage
            saveFloorPlan(bytes)

        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error handling image", e)
            PopupMessage.error(this, getString(R.string.image_load_error)).show()
        }
    }

    private fun saveFloorPlan(imageBytes: ByteArray) {
        try {
            val file = getFloorPlanFile()
            FileOutputStream(file).use { fos ->
                fos.write(imageBytes)
            }

            // Update preview
            loadFloorPlanPreview()

            PopupMessage.success(this, getString(R.string.floor_plan_saved)).show()
            Log.d("SettingsActivity", "Floor plan saved: ${file.absolutePath}, size: ${imageBytes.size} bytes")

        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error saving floor plan", e)
            PopupMessage.error(this, getString(R.string.save_error)).show()
        }
    }

    private fun loadFloorPlanPreview() {
        val file = getFloorPlanFile()

        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    ivFloorPlanPreview.setImageBitmap(bitmap)
                    ivFloorPlanPreview.visibility = View.VISIBLE
                    tvNoFloorPlan.visibility = View.GONE
                    btnRemoveFloorPlan.isEnabled = true
                    return
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error loading floor plan preview", e)
            }
        }

        // No floor plan or error loading
        ivFloorPlanPreview.setImageDrawable(null)
        ivFloorPlanPreview.visibility = View.GONE
        tvNoFloorPlan.visibility = View.VISIBLE
        btnRemoveFloorPlan.isEnabled = false
    }

    private fun removeFloorPlan() {
        val file = getFloorPlanFile()

        if (file.exists()) {
            file.delete()
            loadFloorPlanPreview()
            PopupMessage.info(this, getString(R.string.floor_plan_removed)).show()
            Log.d("SettingsActivity", "Floor plan removed")
        }
    }

    private fun selectBackgroundStyle(style: Int) {
        currentBackgroundStyle = style
        updateBackgroundStyleSelection()

        // Remove custom floor plan when selecting a style
        val file = getFloorPlanFile()
        if (file.exists()) {
            file.delete()
            loadFloorPlanPreview()
        }

        PopupMessage.info(this, getString(R.string.background_selected)).show()
    }

    private fun updateBackgroundStyleSelection() {
        bgStyleButtons.forEachIndexed { index, button ->
            if (index == currentBackgroundStyle) {
                button.foreground = getDrawable(R.drawable.bg_style_selector)
            } else {
                button.foreground = null
            }
        }
    }

    private fun getFloorPlanFile(): File {
        return File(filesDir, FLOOR_PLAN_FILENAME)
    }
}
