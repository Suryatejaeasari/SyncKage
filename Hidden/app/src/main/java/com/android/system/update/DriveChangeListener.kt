package com.android.system.update


import android.util.Log
import kotlinx.coroutines.*
import java.io.File

object DriveChangeListener {
    private const val DRIVE_FOLDER_ID = "drive_folder_id)"
    private var pollingJob: Job? = null

    fun startPolling() {
        Log.d("DriveChangeListener", "🚀 startPolling() called!")

        stopPolling() // Stop any existing polling job

        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d("DriveChangeListener", "🔄 Polling started, checking Drive changes every 10 minutes.")

            checkForDriveChanges() // ✅ Only call once before loop starts

            while (isActive) {
                //delay(10 * 60 * 1000) // Wait before checking again
                Log.d("DriveChangeListener", "🔍 Running checkForDriveChanges() now...")

                try {
                    checkForDriveChanges()
                } catch (e: Exception) {
                    Log.e("DriveChangeListener", "❌ Error in checkForDriveChanges()", e)
                }
            }
        }
    }







    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Log.d("DriveChangeListener", "Stopped polling for Drive changes")
    }

    suspend fun checkForDriveChanges() {
        Log.d("DriveChangeListener", "🔍 Checking Google Drive for changes...")

        val driveFiles = GoogleDriveHelper.listFilesInFolder(DRIVE_FOLDER_ID)

        if (driveFiles.isEmpty()) {
            Log.d("DriveChangeListener", "🚫 No files found in Drive folder.")
            return
        }

        Log.d("DriveChangeListener", "📂 Found ${driveFiles.size} files in Drive folder.")

        for (file in driveFiles) {
            val localFile = File("path${file.name}")
            val driveLastModified = GoogleDriveHelper.getDriveFileModifiedTime(file.id) ?: 0L

            Log.d("DriveChangeListener", "📁 Comparing: ${file.name} (Drive: $driveLastModified, Local: ${localFile.lastModified()})")

            if (!localFile.exists() || driveLastModified > localFile.lastModified()) {
                Log.d("DriveChangeListener", "⬇️ Downloading: ${file.name}")
                if (GoogleDriveHelper.downloadFile(file.id, localFile)) {
                    Log.d("DriveChangeListener", "✅ Downloaded: ${file.name}")
                } else {
                    Log.e("DriveChangeListener", "❌ Failed to download: ${file.name}")
                }
            }
        }
    }






}
