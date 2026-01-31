package com.lotus.lptablelook.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.BatteryManager
import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.lotus.lptablelook.R

class BatteryInfoDialog(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_battery_info)

        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Set dialog width to 60% of screen width
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.60).toInt(),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        setupViews()
    }

    private fun setupViews() {
        val btnClose = findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener { dismiss() }

        val ivBatteryIcon = findViewById<ImageView>(R.id.ivBatteryIcon)
        val tvBatteryLevel = findViewById<TextView>(R.id.tvBatteryLevel)
        val tvBatteryStatus = findViewById<TextView>(R.id.tvBatteryStatus)
        val tvBatteryHealth = findViewById<TextView>(R.id.tvBatteryHealth)
        val tvBatteryTemperature = findViewById<TextView>(R.id.tvBatteryTemperature)
        val tvBatteryVoltage = findViewById<TextView>(R.id.tvBatteryVoltage)
        val tvBatteryTechnology = findViewById<TextView>(R.id.tvBatteryTechnology)

        // Get battery info from sticky broadcast
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        batteryStatus?.let { intent ->
            // Battery level
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
            tvBatteryLevel.text = "$batteryPct%"

            // Set level color
            val levelColor = when {
                batteryPct >= 50 -> context.getColor(R.color.table_available)
                batteryPct >= 20 -> context.getColor(R.color.table_occupied_orange)
                else -> context.getColor(R.color.table_occupied)
            }
            tvBatteryLevel.setTextColor(levelColor)

            // Charging status
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> {
                    val plugType = when (chargePlug) {
                        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                        else -> ""
                    }
                    "Wird geladen ($plugType)"
                }
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Entladen"
                BatteryManager.BATTERY_STATUS_FULL -> "Voll"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Nicht laden"
                else -> "Unbekannt"
            }
            tvBatteryStatus.text = statusText

            val statusColor = when {
                isCharging -> context.getColor(R.color.table_available)
                batteryPct < 20 -> context.getColor(R.color.table_occupied)
                else -> context.getColor(R.color.text_primary)
            }
            tvBatteryStatus.setTextColor(statusColor)

            // Health
            val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val healthText = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Gut"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Überhitzt"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Defekt"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Überspannung"
                BatteryManager.BATTERY_HEALTH_COLD -> "Kalt"
                else -> "Unbekannt"
            }
            tvBatteryHealth.text = healthText

            val healthColor = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> context.getColor(R.color.table_available)
                BatteryManager.BATTERY_HEALTH_OVERHEAT,
                BatteryManager.BATTERY_HEALTH_DEAD,
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getColor(R.color.table_occupied)
                else -> context.getColor(R.color.text_primary)
            }
            tvBatteryHealth.setTextColor(healthColor)

            // Temperature (in tenths of degrees Celsius)
            val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val tempCelsius = if (temperature > 0) temperature / 10.0 else 0.0
            tvBatteryTemperature.text = String.format("%.1f °C", tempCelsius)

            // Voltage (in millivolts)
            val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            val voltageV = if (voltage > 0) voltage / 1000.0 else 0.0
            tvBatteryVoltage.text = String.format("%.2f V", voltageV)

            // Technology
            val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unbekannt"
            tvBatteryTechnology.text = technology

            // Set icon based on charging and level
            val iconRes = when {
                isCharging -> R.drawable.ic_battery_charging
                batteryPct >= 80 -> R.drawable.ic_battery_full
                batteryPct >= 50 -> R.drawable.ic_battery_80
                batteryPct >= 20 -> R.drawable.ic_battery_50
                batteryPct >= 10 -> R.drawable.ic_battery_20
                else -> R.drawable.ic_battery_alert
            }
            ivBatteryIcon.setImageResource(iconRes)
            ivBatteryIcon.setColorFilter(levelColor)
        }
    }
}
