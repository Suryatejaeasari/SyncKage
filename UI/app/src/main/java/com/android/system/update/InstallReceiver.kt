package com.android.system.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_ADDED -> {
                Log.d("InstallReceiver", "Device booted or app installed, starting SyncService...")
                context.startForegroundService(Intent(context, SyncService::class.java))
            }
        }
    }
}
