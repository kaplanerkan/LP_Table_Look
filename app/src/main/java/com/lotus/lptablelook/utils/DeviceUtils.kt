package com.lotus.lptablelook.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceUtils {

    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""

        val serial = getDeviceSerial()

        return (androidId + serial).uppercase()
    }

    @Suppress("DEPRECATION")
    @SuppressLint("HardwareIds")
    private fun getDeviceSerial(): String {
        return try {
            Build.SERIAL ?: "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
}
