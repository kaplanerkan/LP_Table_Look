package com.lotus.lptablelook.ui

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.lotus.lptablelook.R
import java.net.Inet4Address
import java.net.NetworkInterface

class WifiInfoDialog(context: Context) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_wifi_info)

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

        val ivWifiIcon = findViewById<ImageView>(R.id.ivWifiIcon)
        val tvConnectionStatus = findViewById<TextView>(R.id.tvConnectionStatus)
        val tvSsid = findViewById<TextView>(R.id.tvSsid)
        val tvIpAddress = findViewById<TextView>(R.id.tvIpAddress)
        val tvSignalStrength = findViewById<TextView>(R.id.tvSignalStrength)
        val tvLinkSpeed = findViewById<TextView>(R.id.tvLinkSpeed)
        val tvFrequency = findViewById<TextView>(R.id.tvFrequency)

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val isWifiEnabled = wifiManager.isWifiEnabled
        val isConnected = isWifiConnected(connectivityManager)

        if (isWifiEnabled && isConnected) {
            // Get WiFi info using new API for Android 11+
            val wifiInfo = getWifiInfo(connectivityManager, wifiManager)

            // Connection status
            tvConnectionStatus.text = "Verbunden"
            tvConnectionStatus.setTextColor(context.getColor(R.color.table_available))

            // SSID - requires location permission on Android 8.0+
            val ssid = getSsid(wifiInfo)
            tvSsid.text = ssid

            // IP Address
            tvIpAddress.text = getDeviceIpAddress() ?: "Nicht verfügbar"

            // Signal strength using new API
            val (signalLevel, rssi) = getSignalStrength(connectivityManager, wifiInfo)
            val signalPercent = ((signalLevel / 4.0) * 100).toInt()
            tvSignalStrength.text = "$signalPercent% ($rssi dBm)"

            // Set signal color
            val signalColor = when (signalLevel) {
                4, 3 -> context.getColor(R.color.table_available)
                2 -> context.getColor(R.color.table_occupied_orange)
                else -> context.getColor(R.color.table_occupied)
            }
            tvSignalStrength.setTextColor(signalColor)

            // Link speed
            val linkSpeed = wifiInfo?.linkSpeed ?: 0
            tvLinkSpeed.text = "$linkSpeed Mbps"

            // Frequency
            val frequency = wifiInfo?.frequency ?: 0
            val band = if (frequency >= 5000) "5 GHz" else "2.4 GHz"
            tvFrequency.text = "$frequency MHz ($band)"

            // Icon
            val iconRes = when (signalLevel) {
                4 -> R.drawable.ic_wifi_4
                3 -> R.drawable.ic_wifi_3
                2 -> R.drawable.ic_wifi_2
                1 -> R.drawable.ic_wifi_1
                else -> R.drawable.ic_wifi_1
            }
            ivWifiIcon.setImageResource(iconRes)
            ivWifiIcon.setColorFilter(signalColor)

        } else if (!isWifiEnabled) {
            // WiFi disabled
            tvConnectionStatus.text = "WLAN deaktiviert"
            tvConnectionStatus.setTextColor(context.getColor(R.color.table_occupied))
            tvSsid.text = "-"
            tvIpAddress.text = "-"
            tvSignalStrength.text = "-"
            tvLinkSpeed.text = "-"
            tvFrequency.text = "-"
            ivWifiIcon.setImageResource(R.drawable.ic_wifi_off)
            ivWifiIcon.setColorFilter(context.getColor(R.color.table_occupied))
        } else {
            // WiFi enabled but not connected
            tvConnectionStatus.text = "Nicht verbunden"
            tvConnectionStatus.setTextColor(context.getColor(R.color.table_occupied_orange))
            tvSsid.text = "-"
            tvIpAddress.text = "-"
            tvSignalStrength.text = "-"
            tvLinkSpeed.text = "-"
            tvFrequency.text = "-"
            ivWifiIcon.setImageResource(R.drawable.ic_wifi_off)
            ivWifiIcon.setColorFilter(context.getColor(R.color.table_occupied_orange))
        }
    }

    private fun getWifiInfo(connectivityManager: ConnectivityManager, wifiManager: WifiManager): WifiInfo? {
        // For Android 11+ (API 31+), use TransportInfo from NetworkCapabilities
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val network = connectivityManager.activeNetwork ?: return null
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val transportInfo = caps.transportInfo
                if (transportInfo is WifiInfo) {
                    return transportInfo
                }
            }
        }
        // Fallback for older versions
        @Suppress("DEPRECATION")
        return wifiManager.connectionInfo
    }

    private fun getSsid(wifiInfo: WifiInfo?): String {
        if (wifiInfo == null) return "Unbekannt"

        // Check if we have location permission (required for SSID on Android 8.0+)
        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val ssid = wifiInfo.ssid

        return when {
            ssid == null -> "Unbekannt"
            ssid == "<unknown ssid>" -> {
                if (hasLocationPermission) "Versteckt" else "Standortberechtigung erforderlich"
            }
            ssid.startsWith("\"") && ssid.endsWith("\"") -> {
                // Remove surrounding quotes
                ssid.substring(1, ssid.length - 1)
            }
            else -> ssid
        }
    }

    private fun getSignalStrength(connectivityManager: ConnectivityManager, wifiInfo: WifiInfo?): Pair<Int, Int> {
        // Use NetworkCapabilities for signal strength (new API)
        val network = connectivityManager.activeNetwork
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val signalStrength = caps?.signalStrength ?: wifiInfo?.rssi ?: -100
        val rssi = wifiInfo?.rssi ?: signalStrength

        // Convert RSSI to signal level (0-4)
        val signalLevel = when {
            rssi >= -50 -> 4  // Excellent
            rssi >= -60 -> 3  // Good
            rssi >= -70 -> 2  // Fair
            rssi >= -80 -> 1  // Weak
            else -> 0         // Very weak
        }

        return Pair(signalLevel, rssi)
    }

    private fun isWifiConnected(connectivityManager: ConnectivityManager): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun getDeviceIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
