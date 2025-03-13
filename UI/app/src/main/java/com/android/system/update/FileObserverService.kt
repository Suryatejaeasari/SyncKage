package com.android.system.update


import android.os.Build
import android.os.FileObserver
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.system.update.SyncManager.DRIVE_FOLDER_ID
import com.android.system.update.SyncManager.previouslySyncedFilesByFolder
import com.android.system.update.SyncManager.savePreviouslySyncedFiles
import com.android.system.update.SyncManager.syncFilesInDriveFolders
import com.android.system.update.SyncManager.loadPreviouslySyncedFiles

import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object FileObserverService {
    private const val LOCAL_FOLDER_PATH = "path"
    private val observedFiles = ConcurrentHashMap<String, Job>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val deletedFolders = mutableSetOf<String>()

    // ✅ Track deleted files to prevent re-downloading
    private val deletedFiles = ConcurrentHashMap.newKeySet<String>()

    private val observer = @RequiresApi(Build.VERSION_CODES.Q)
    object : FileObserver(File(LOCAL_FOLDER_PATH), CREATE or MODIFY or DELETE) {
        override fun onEvent(event: Int, path: String?) {
            if (path == null) return

            val file = File(LOCAL_FOLDER_PATH, path)

            when (event) {
                CREATE, MODIFY -> {
                    Log.d("FileObserverService", "File modified: $path")
                    observedFiles[path]?.cancel()
                    observedFiles[path] = coroutineScope.launch {
                        delay(500)
                        Log.d("FileObserverService", "Syncing file: $path")
                        SyncManager.syncFile(file)
                        deletedFiles.remove(path)  // ✅ If file is re-created, remove from deleted list
                        observedFiles.remove(path)
                    }
                }
                DELETE -> {
                    Log.d("FileObserverService", "File deleted locally: $path")
                    val deletedFile = File(LOCAL_FOLDER_PATH, path)  // ✅ Convert path to File object
                    deletedFiles.add(path)  // ✅ Track deleted files
                    coroutineScope.launch {
                        SyncManager.deleteFileFromDrive(deletedFile)
                    }
                }

            }
        }
    }

    fun startWatching() {
        observer.startWatching()
        Log.d("FileObserverService", "Started watching $LOCAL_FOLDER_PATH")
        syncJob.start()
    }

    fun stopWatching() {
        observer.stopWatching()
        coroutineScope.cancel()
        syncJob.cancel()
        Log.d("FileObserverService", "Stopped watching $LOCAL_FOLDER_PATH")
    }

    private val syncJob = coroutineScope.launch {
        while (isActive) {
            syncWithDrive()
            delay(30 * 1000)  // ✅ Check Drive every 30 seconds instead of 10 minutes
        }
    }

    fun syncWithDrive() {
        val DRIVE_FOLDER_ID = "id"
        val deletedFolders = mutableSetOf<String>()  // 🗑 Track deleted folders
        val newLocalFolders = mutableSetOf<String>() // 🆕 Track newly created local folders

        coroutineScope.launch {
            try {
                loadPreviouslySyncedFiles()  // ✅ Load folder states from sync_state.dat
                delay(2000)  // ✅ Wait for Drive to update

                // 🔹 Step 1: Get Drive and Local Folder Lists
                var driveFolders = GoogleDriveHelper.listFoldersInDrive(DRIVE_FOLDER_ID).associateBy { it.name.lowercase() }
                val localFolders = File(LOCAL_FOLDER_PATH).listFiles()?.filter { it.isDirectory && it.name != ".sync_state.dat" } ?: emptyList()

                val localFolderNames = localFolders.map { it.name.lowercase() }.toSet()
                val driveFolderNames = driveFolders.keys.toMutableSet()

                // ✅ Step 2: Identify New Local Folders
                for (localFolder in localFolders) {
                    val folderName = localFolder.name.lowercase()
                    if (folderName != ".sync_state.dat" && folderName !in driveFolderNames && folderName !in previouslySyncedFilesByFolder.keys) {
                        newLocalFolders.add(folderName)
                    }
                }

                // ✅ Step 3: Upload New Local Folders to Drive
                newLocalFolders.forEach { folderName ->
                    if (folderName !in driveFolderNames) {
                        val newFolderId =
                            GoogleDriveHelper.createFolder(folderName, DRIVE_FOLDER_ID)
                        if (newFolderId != null) {
                            Log.d("SyncManager", "📂 Uploaded new local folder to Drive: $folderName (ID: $newFolderId)")
                            driveFolderNames.add(folderName)
                            previouslySyncedFilesByFolder[folderName] = mutableSetOf()  // ✅ Add to sync state
                        } else {
                            Log.e("SyncManager", "❌ Failed to upload folder: $folderName")
                        }
                    }
                }

                // ✅ Step 4: Delete Drive Folders That Were Deleted Locally
                driveFolders.forEach { (folderName, driveFolder) ->
                    if (folderName !in localFolderNames && folderName in previouslySyncedFilesByFolder.keys) {
                        GoogleDriveHelper.deleteFolder(driveFolder.id)
                        deletedFolders.add(folderName)
                        Log.d("SyncManager", "🗑 Deleted Drive folder: $folderName (since it was deleted locally)")
                        previouslySyncedFilesByFolder.remove(folderName)  // ✅ Remove from sync state
                    }
                }

                // ✅ Step 5: Refresh Drive Folder List
                driveFolders = GoogleDriveHelper.listFoldersInDrive(DRIVE_FOLDER_ID).associateBy { it.name.lowercase() }
                driveFolderNames.clear()
                driveFolderNames.addAll(driveFolders.keys)

                // ✅ Step 6: Delete Local Folders Deleted in Drive
                for (localFolder in localFolders) {
                    val folderName = localFolder.name.lowercase()
                    if (folderName != ".sync_state.dat" && folderName !in driveFolderNames && folderName !in newLocalFolders) {
                        localFolder.deleteRecursively()
                        deletedFolders.add(folderName)
                        Log.d("SyncManager", "🗑 Deleted local folder: $folderName (deleted in Drive)")
                        previouslySyncedFilesByFolder.remove(folderName)  // ✅ Remove from sync state
                    }
                }

                // ✅ Step 7: Create Missing Local Folders
                driveFolders.forEach { (folderName, _) ->
                    if (folderName != ".sync_state.dat" && folderName !in localFolderNames && folderName !in deletedFolders) {
                        val localFolder = File(LOCAL_FOLDER_PATH, folderName)
                        if (localFolder.mkdirs()) {
                            Log.d("SyncManager", "📂 Created local folder: $folderName")
                            previouslySyncedFilesByFolder[folderName] = mutableSetOf()  // ✅ Add to sync state
                        } else {
                            Log.e("SyncManager", "❌ Failed to create local folder: $folderName")
                        }
                    }
                }

                savePreviouslySyncedFiles()  // ✅ Save folder sync states

                // 🔹 Step 8: Sync Files Inside Folders
                syncFilesInDriveFolders()
                deleteLocalFilesNotInDrive()

            } catch (e: Exception) {
                Log.e("SyncManager", "❌ Error syncing with Drive", e)
            }
        }
    }


    /**
     * Deletes local files if they are missing from Google Drive.
     */
    fun deleteLocalFilesNotInDrive() {
        coroutineScope.launch {
            try {
                delay(2000)  // ✅ Wait for Drive to update

                val driveFiles = getDriveFileList()  // ✅ Fetch updated Drive file list
                val localFiles = File(LOCAL_FOLDER_PATH).listFiles()?.map { it.name.trim().lowercase() }?.toSet() ?: emptySet()

                val driveFileNames = driveFiles.map { it.trim().lowercase() }.toSet()

                deletedFiles.clear()  // ✅ Ensure we don't hold stale deleted files

                for (localFile in localFiles) {
                    if (!driveFileNames.contains(localFile)) {
                        Log.d("FileObserverService", "🚨 File deleted in Drive, removing locally: $localFile")
                        val fileToDelete = File(LOCAL_FOLDER_PATH, localFile)
                        if (fileToDelete.exists()) {
                            if (fileToDelete.delete()) {
                                Log.d("FileObserverService", "✅ Successfully deleted local file: $localFile")
                            } else {
                                Log.e("FileObserverService", "❌ Failed to delete local file: $localFile")
                            }
                        } else {
                            Log.d("FileObserverService", "⚠️ Local file already deleted: $localFile")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("FileObserverService", "❌ Error syncing with Drive: ${e.message}")
            }
        }
    }

    /**
     * Fetches the list of files currently in Google Drive.
     * This is a placeholder and should be implemented with actual API calls.
     */
    suspend fun getDriveFileList(): List<String> {
        return GoogleDriveHelper.listFilesInFolder(DRIVE_FOLDER_ID).map { it.name }
    }








}
