package com.android.system.update

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "App started")

        requestPermissionsAndStartService()
    }

    private fun requestPermissionsAndStartService() {
        val requiredPermissions = arrayOf(

            Manifest.permission.FOREGROUND_SERVICE
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startSyncService()
        } else {
            requestPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Log.d("MainActivity", "All permissions granted, starting service...")
                startSyncService()
            } else {
                Log.e("MainActivity", "Permissions denied! Sync will not work properly.")
            }
        }

    private fun startSyncService() {
        val serviceIntent = Intent(this, SyncService::class.java)
        startForegroundService(serviceIntent)
        ContextCompat.startForegroundService(this, serviceIntent)
    }
}
