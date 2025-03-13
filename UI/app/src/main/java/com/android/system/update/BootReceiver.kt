package com.android.system.update


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device reboot detected, starting SyncService...")
            context.startForegroundService(Intent(context, SyncService::class.java))
        }
    }
}
