package com.android.system.update


import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.d("DeviceAdminReceiver", "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.d("DeviceAdminReceiver", "Device admin disabled")
    }
}
