package com.lotus.lptablelook

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.lotus.lptablelook.ui.PopupMessage
import androidx.activity.enableEdgeToEdge
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

class SettingsActivity : AppCompatActivity() {

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

    private lateinit var repository: TableRepository
    private lateinit var syncService: SyncService
    private var currentSettings: Settings? = null

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

        // Show device ID
        tvDeviceId.text = syncService.getDeviceId()
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
    }

    private fun saveSettings() {
        val socketIp = etSocketIp.text?.toString()?.trim() ?: ""
        val autoConnect = switchAutoConnect.isChecked
        val restaurantName = etRestaurantName.text?.toString()?.trim() ?: ""
        val tableScale = seekBarTableScale.progress / 100f

        val settings = Settings(
            id = 1,
            socketIp = socketIp,
            socketPort = FIXED_PORT,
            autoConnect = autoConnect,
            restaurantName = restaurantName,
            tableScaleDefault = tableScale
        )

        Log.d("SettingsActivity", "saveSettings - saving: $settings")

        lifecycleScope.launch {
            repository.saveSettings(settings)
            Log.d("SettingsActivity", "saveSettings - saved successfully")
            PopupMessage.success(this@SettingsActivity, "Einstellungen gespeichert").show()
            finish()
        }
    }

    private fun testConnection() {
        val ip = etSocketIp.text?.toString()?.trim() ?: ""

        if (ip.isEmpty()) {
            showStatus("Bitte IP eingeben", false)
            return
        }

        Log.d("SettingsActivity", "testConnection called with IP: $ip, Port: $FIXED_PORT")

        setButtonsEnabled(false)
        val progressDialog = ProgressDialog.showConnectionTest(this)

        lifecycleScope.launch {
            Log.d("SettingsActivity", "Initializing syncService...")
            syncService.initialize(ip, FIXED_PORT)
            Log.d("SettingsActivity", "syncService initialized")

            progressDialog.updateMessage("Gerät wird registriert...")

            Log.d("SettingsActivity", "Calling testConnectionAndRegister...")
            when (val result = syncService.testConnectionAndRegister()) {
                is SyncService.SyncResult.Success -> {
                    showStatus("Verbindung erfolgreich! Gerät registriert.", true)
                    // Save IP to Room on successful connection
                    saveConnectionSettings(ip)
                }
                is SyncService.SyncResult.Error -> {
                    showStatus("Fehler: ${result.message}", false)
                }
                is SyncService.SyncResult.NoConnection -> {
                    showStatus("Keine WiFi-Verbindung", false)
                }
            }

            progressDialog.dismiss()
            setButtonsEnabled(true)
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
            showStatus("Bitte IP eingeben", false)
            return
        }

        setButtonsEnabled(false)
        val progressDialog = ProgressDialog.showSync(this)

        lifecycleScope.launch {
            syncService.initialize(ip, FIXED_PORT)

            val deviceId = syncService.getDeviceId()

            progressDialog.updateMessage("Plattformen werden geladen...")

            when (val result = syncService.syncAll(deviceId)) {
                is SyncService.SyncResult.Success -> {
                    showStatus("Synchronisierung erfolgreich!", true)
                    // Save IP to Room on successful sync
                    saveConnectionSettings(ip)
                    PopupMessage.success(this@SettingsActivity, "Daten wurden aktualisiert").show()
                }
                is SyncService.SyncResult.Error -> {
                    showStatus("Fehler: ${result.message}", false)
                }
                is SyncService.SyncResult.NoConnection -> {
                    showStatus("Keine WiFi-Verbindung", false)
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
}
