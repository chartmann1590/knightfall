package com.chartmann.knightfall.data.feedback

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.pm.PackageInfoCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DiagnosticsHelper {
    fun getDiagnosticsMarkdown(context: Context): String {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
        val versionName = packageInfo?.versionName ?: "Unknown"
        val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val freeMem = (memoryInfo?.availMem ?: 0L) / (1024 * 1024)
        val totalMem = (memoryInfo?.totalMem ?: 0L) / (1024 * 1024)

        val statFs = try {
            StatFs(Environment.getDataDirectory().path)
        } catch (e: Exception) {
            null
        }
        val freeStorage = statFs?.let { it.availableBytes / (1024 * 1024 * 1024f) } ?: 0f
        val totalStorage = statFs?.let { it.totalBytes / (1024 * 1024 * 1024f) } ?: 0f

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val timestamp = dateFormat.format(Date())

        return """
            ## Diagnostics

            - App: Knightfall
            - Package: ${context.packageName}
            - Version: $versionName ($versionCode)
            - Device: ${Build.MODEL}
            - Brand: ${Build.BRAND}
            - Manufacturer: ${Build.MANUFACTURER}
            - Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}
            - Locale: ${Locale.getDefault()}
            - Time Zone: ${TimeZone.getDefault().id}
            - Storage Free/Total: ${String.format(Locale.US, "%.2f", freeStorage)} GB / ${String.format(Locale.US, "%.2f", totalStorage)} GB
            - Memory Free/Total: ${freeMem} MB / ${totalMem} MB
            - Timestamp: $timestamp
        """.trimIndent()
    }
}
