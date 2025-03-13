package com.android.system.update

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var logTable: TableLayout
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private var logcatProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logTable = findViewById(R.id.logTable)
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)

        requestPermissions()
        addTableHeader()

        startServiceButton.setOnClickListener {
            clearLogs()
            startSyncService()
        }

        stopServiceButton.setOnClickListener {
            clearLogs()
            stopSyncService()
        }
    }

    private fun requestPermissions() {
        val requiredPermissions = arrayOf(Manifest.permission.FOREGROUND_SERVICE)

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Log.d("MainActivity", "Permissions granted.")
            } else {
                Log.e("MainActivity", "Permissions denied.")
            }
        }

    private fun startSyncService() {
        val serviceIntent = Intent(this, SyncService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        addLog("✅", "Sync Service Started")
        startLogcatListener()
    }

    private fun stopSyncService() {
        val serviceIntent = Intent(this, SyncService::class.java)
        stopService(serviceIntent)
        addLog("🛑", "Sync Service Stopped")
        stopLogcatListener()
    }

    private fun startLogcatListener() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                logcatProcess = Runtime.getRuntime().exec("logcat -s SyncManager")
                val reader = BufferedReader(InputStreamReader(logcatProcess!!.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.contains("SyncManager")) {
                        val formattedLog = cleanLogMessage(line)
                        runOnUiThread { addLog("ℹ️", formattedLog) }
                    }
                    delay(500)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error reading logs", e)
            }
        }
    }

    private fun cleanLogMessage(rawLog: String): String {
        // Example log: "03-02 15:44:13.314 30284-30339 SyncManager: File Uploaded Successfully"
        return rawLog.substringAfter("SyncManager:").trim() // Extract only message part
    }

    private fun stopLogcatListener() {
        logcatProcess?.destroy()
        logcatProcess = null
    }

    private fun formatLogMessage(rawLog: String): String {
        return when {
            rawLog.contains("File Uploaded") -> "File successfully uploaded"
            rawLog.contains("File Downloaded") -> "File downloaded from server"
            rawLog.contains("Sync Completed") -> "Synchronization finished"
            rawLog.contains("Error") -> "⚠️ Error during sync"
            else -> rawLog.replace("SyncManager:", "").trim()
        }
    }

    private fun addLog(icon: String, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        val row = TableRow(this).apply {
            addView(createCell(timestamp))
            addView(createCell(icon))
            addView(createCell(message))
        }

        logTable.addView(row, 1) // Add below headers
    }

    private fun createCell(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setPadding(8, 4, 8, 4)
            setTextColor(resources.getColor(android.R.color.white, null))
        }
    }

    private fun clearLogs() {
        logTable.removeAllViews()
        addTableHeader()
    }

    private fun addTableHeader() {
        val headerRow = TableRow(this).apply {
            addView(createHeaderCell("Time"))
            addView(createHeaderCell("⚡"))
            addView(createHeaderCell("Log Message"))
        }
        logTable.addView(headerRow)
    }

    private fun createHeaderCell(text: String): TextView {
        return createCell(text).apply {
            setTextColor(resources.getColor(android.R.color.holo_blue_light, null))
            textSize = 16f
        }
    }
}
