package com.android.system.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SyncService : Service() {
    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        Log.d("SyncService", "✅ Service started, initializing sync...")

        FileObserverService.startWatching()
        Log.d("SyncService", "✅ FileObserverService started")

        DriveChangeListener.startPolling()

        // ✅ Run checkForDriveChanges() in a coroutine (avoids coroutine scope errors)
        CoroutineScope(Dispatchers.IO).launch {
            DriveChangeListener.checkForDriveChanges()
        }

        Log.d("SyncService", "✅ DriveChangeListener started")
    }




    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SyncService", "SyncService restarted (if killed)")
        return START_STICKY // Ensures the service restarts if killed
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SyncService", "Service stopped, cleaning up...")
        FileObserverService.stopWatching()
        DriveChangeListener.stopPolling()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "sync_service_channel"
        val channelName = "Sync Service"

        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("Sync Running")
            .setContentText("Monitoring and syncing files...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .build()

        startForeground(1, notification)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("SyncService", "SyncService was killed! Restarting...")
        val restartIntent = Intent(applicationContext, SyncService::class.java)
        applicationContext.startForegroundService(restartIntent)
    }



}
