package com.android.system.update

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object SyncManager {
    const val DRIVE_FOLDER_ID = "drive_folder_id"
    private val deletedFiles = ConcurrentHashMap.newKeySet<String>()
    private val recentlyUploadedFiles = ConcurrentHashMap.newKeySet<String>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val LOCAL_FOLDER_PATH = "path"



    suspend fun syncFile(localFile: File) {
        try {
            val fileName = localFile.name
            val driveFileId = GoogleDriveHelper.findFileInDrive(fileName, DRIVE_FOLDER_ID)
            val localLastModified = localFile.lastModified()

            if (driveFileId == null) {
                Log.d("SyncManager", "🚀 Uploading new file: $fileName")
                GoogleDriveHelper.uploadFile(localFile, DRIVE_FOLDER_ID)

                // ✅ Track recently uploaded file
                recentlyUploadedFiles.add(fileName)

                // ✅ Remove it from tracking after some time (avoid unnecessary updates)
                coroutineScope.launch {
                    delay(10_000)  // 10 seconds buffer
                    recentlyUploadedFiles.remove(fileName)
                }
                return
            }

            val driveLastModified = GoogleDriveHelper.getDriveFileModifiedTime(driveFileId) ?: 0L

            // 🚨 Prevent unnecessary updates if recently uploaded
            if (recentlyUploadedFiles.contains(fileName)) {
                Log.d("SyncManager", "Skipping update for recently uploaded file: $fileName")
                return
            }

            // ✅ Introduce a buffer threshold to prevent microsecond timestamp mismatches
            val timeDifference = localLastModified - driveLastModified
            val TIME_THRESHOLD = 2000L  // 2 seconds

            when {
                timeDifference > TIME_THRESHOLD -> {
                    Log.d("SyncManager", "Updating Drive file: $fileName")
                    GoogleDriveHelper.uploadFileToDrive(localFile, DRIVE_FOLDER_ID)
                }
                timeDifference < -TIME_THRESHOLD -> {
                    Log.d("SyncManager", "Skipping download for locally deleted file: $fileName")
                }
                else -> Log.d("SyncManager", "⏳ File is already in sync (within time threshold): $fileName")
            }
        } catch (e: Exception) {
            Log.e("SyncManager", "Error syncing file: ${localFile.name}", e)
        }
    }


    fun deleteFileFromDrive(localFile: File) {
        try {
            val fileName = localFile.name
            deletedFiles.add(fileName)  // ✅ Mark file as deleted before anything

            val driveFileId = GoogleDriveHelper.findFileInDrive(fileName, DRIVE_FOLDER_ID)
            if (driveFileId != null) {
                Log.d("SyncManager", "Deleting from Drive: $fileName")
                GoogleDriveHelper.deleteFile(driveFileId)
            } else {
                Log.d("SyncManager", "File not found in Drive: $fileName")
            }

            // ✅ Ensure local file is deleted
            if (localFile.exists()) {
                val deleted = localFile.delete()
                if (deleted) {
                    Log.d("SyncManager", "✅ Deleted local file after Drive removal: $fileName")
                } else {
                    Log.e("SyncManager", "❌ Failed to delete local file: $fileName")
                }
            }

        } catch (e: Exception) {
            Log.e("SyncManager", "Error deleting file from Drive: ${localFile.name}", e)
        }
    }

    suspend fun getDriveFileList(): Set<String> {
        return try {
            val driveFiles = GoogleDriveHelper.listFilesInFolder(DRIVE_FOLDER_ID)
            driveFiles.map { it.name }.toSet()  // ✅ Extract file names
        } catch (e: Exception) {
            Log.e("SyncManager", "Error fetching Drive file list", e)
            emptySet()
        }
    }
    suspend fun syncLocalFoldersToDrive() {

        val localFolders = File(LOCAL_FOLDER_PATH).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet() ?: emptySet()

        val driveFolders = getDriveFolderList()  // ✅ Fetch existing folders in Drive

        for (folderName in localFolders) {
            if (!driveFolders.contains(folderName)) {
                Log.d("SyncManager", "🚀 Creating folder in Drive: $folderName")
                GoogleDriveHelper.createFolder(folderName, DRIVE_FOLDER_ID)
            }
        }
    }
    suspend fun syncDriveFoldersToLocal() {
        val localFolders = File(LOCAL_FOLDER_PATH).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet() ?: emptySet()

        val driveFolders = getDriveFolderList()  // ✅ Fetch existing folders in Drive

        for (folderName in driveFolders) {
            val localFolder = File(LOCAL_FOLDER_PATH, folderName)
            if (!localFolder.exists()) {
                Log.d("SyncManager", "📂 Creating local folder: $folderName")
                localFolder.mkdir()  // ✅ Create the folder locally
            }
        }
    }


    private val pendingDeletions = ConcurrentHashMap.newKeySet<String>()
    val previouslySyncedFilesByFolder = ConcurrentHashMap<String, MutableSet<String>>()
    private val SYNC_STATE_FILE = File(LOCAL_FOLDER_PATH, ".sync_state.dat")

    suspend fun syncFilesInDriveFolders() {
        Log.d("SyncManager", "🔄 Starting recursive file synchronization")

        try {
            loadPreviouslySyncedFiles()

            val driveFolders = GoogleDriveHelper.listFoldersInDrive(DRIVE_FOLDER_ID).associateBy { it.name.lowercase() }
            Log.d("SyncManager", "📁 Found ${driveFolders.size} folders in Drive")

            for ((folderName, driveFolder) in driveFolders) {
                val folderId = driveFolder.id
                val localFolder = File(LOCAL_FOLDER_PATH, folderName)

                if (!localFolder.exists() || !localFolder.isDirectory) {
                    Log.d("SyncManager", "📂 Creating local folder: $folderName")
                    localFolder.mkdirs() // Ensure all parent folders are created
                }

                syncFolderContents(folderId, localFolder)
            }

            syncLocalSubfolders(File(LOCAL_FOLDER_PATH), DRIVE_FOLDER_ID) // Ensure local subfolders are also synced

            Log.d("SyncManager", "✅ Recursive file synchronization completed successfully")
        } catch (e: Exception) {
            Log.e("SyncManager", "❌ Sync error", e)
        }
    }

    private suspend fun syncFolderContents(driveFolderId: String, localFolder: File) {
        try {
            val driveFiles = GoogleDriveHelper.listFilesInFolder(driveFolderId).associateBy { it.name.lowercase() }
            val localFiles = localFolder.listFiles()
                ?.filter { it.name != ".sync_state.dat" } // Ensure sync_state is skipped
                ?.associateBy { it.name.lowercase() } ?: emptyMap()

            val folderPath = localFolder.absolutePath
            val previouslySyncedFiles = previouslySyncedFilesByFolder.getOrPut(folderPath) { mutableSetOf() }
            val currentSyncedFiles = (driveFiles.keys + localFiles.keys).toMutableSet()

            // PASS 1: Handle deletions based on sync state
            for (fileName in previouslySyncedFiles) {
                val existsInDrive = driveFiles.containsKey(fileName)
                val existsLocally = localFiles.containsKey(fileName)

                if (!existsLocally && existsInDrive) { // Deleted locally -> remove from Drive
                    Log.d("SyncManager", "🗑️ File deleted locally, removing from Drive: $fileName")
                    val driveFileId = GoogleDriveHelper.findFileInDrive(fileName, driveFolderId)
                    if (driveFileId != null) GoogleDriveHelper.deleteFile(driveFileId)
                    currentSyncedFiles.remove(fileName)
                }

                if (!existsInDrive && existsLocally) { // Deleted from Drive -> remove locally
                    Log.d("SyncManager", "🗑️ File deleted from Drive, removing locally: $fileName")
                    localFiles[fileName]?.delete()
                    currentSyncedFiles.remove(fileName)
                }
            }

            // PASS 2: Upload new or modified local files and folders to Drive
            for ((_, localFile) in localFiles) {
                if (!driveFiles.containsKey(localFile.name.lowercase()) && !previouslySyncedFiles.contains(localFile.name.lowercase())) {
                    if (localFile.isDirectory) {
                        Log.d("SyncManager", "📁 Creating folder in Drive: ${localFile.name}")
                        val newFolderId = GoogleDriveHelper.createFolder(localFile.name, driveFolderId)
                        if (newFolderId != null) {
                            syncFolderContents(newFolderId, localFile)
                        } else {
                            Log.e("SyncManager", "❌ Failed to create folder in Drive: ${localFile.name}")
                        }
                    } else {
                        Log.d("SyncManager", "⬆️ Uploading new file to Drive: ${localFile.name}")
                        GoogleDriveHelper.uploadFile(localFile, driveFolderId)
                        recentlyUploadedFiles.add(localFile.name.lowercase())
                        currentSyncedFiles.add(localFile.name.lowercase())
                    }
                }
            }

            // Refresh driveFiles after uploads
            val updatedDriveFiles = GoogleDriveHelper.listFilesInFolder(driveFolderId).associateBy { it.name.lowercase() }

            // PASS 3: Execute pending deletions (remove from Drive)
            for (driveFileName in pendingDeletions) {
                if (updatedDriveFiles.containsKey(driveFileName)) {
                    Log.d("SyncManager", "✅ Deleting from Drive: $driveFileName")
                    val driveFileId = GoogleDriveHelper.findFileInDrive(driveFileName, driveFolderId)
                    if (driveFileId != null) GoogleDriveHelper.deleteFile(driveFileId)
                    pendingDeletions.remove(driveFileName)
                    currentSyncedFiles.remove(driveFileName)
                }
            }

            // PASS 4: Download new or updated files and folders from Drive
            for ((_, driveFile) in updatedDriveFiles) {
                val localFile = File(localFolder, driveFile.name)
                if (!localFiles.containsKey(driveFile.name.lowercase()) && !previouslySyncedFiles.contains(driveFile.name.lowercase())) {
                    if (driveFile.mimeType == "application/vnd.google-apps.folder") {
                        Log.d("SyncManager", "📁 Creating local folder: ${driveFile.name}")
                        localFile.mkdirs()
                        syncFolderContents(driveFile.id, localFile)
                    } else {
                        Log.d("SyncManager", "⬇️ Downloading from Drive: ${driveFile.name}")
                        GoogleDriveHelper.downloadFile(driveFile.id, localFile)
                        currentSyncedFiles.add(driveFile.name.lowercase())
                    }
                }
            }

            // Update sync state for current folder only
            previouslySyncedFiles.clear()
            previouslySyncedFiles.addAll(currentSyncedFiles)
            previouslySyncedFilesByFolder[folderPath] = previouslySyncedFiles
            savePreviouslySyncedFiles() // Save immediately for each folder update

        } catch (e: Exception) {
            Log.e("SyncManager", "❌ Error syncing folder: ${localFolder.name}", e)
        }
    }

    private suspend fun syncLocalSubfolders(folder: File, parentDriveFolderId: String) {
        folder.listFiles()?.filter { it.isDirectory && it.name != ".sync_state.dat" }?.forEach { subfolder ->
            Log.d("SyncManager", "🔄 Syncing subfolder: ${subfolder.name}")
            val driveFolderId = GoogleDriveHelper.findFolderInDrive(subfolder.name, parentDriveFolderId) ?: GoogleDriveHelper.createFolder(subfolder.name, parentDriveFolderId)
            if (driveFolderId != null) {
                syncFolderContents(driveFolderId, subfolder)
                syncLocalSubfolders(subfolder, driveFolderId)
            } else {
                Log.e("SyncManager", "❌ Failed to sync subfolder: ${subfolder.name}")
            }
        }
    }

    fun savePreviouslySyncedFiles() {
        try {
            ObjectOutputStream(SYNC_STATE_FILE.outputStream()).use { it.writeObject(
                previouslySyncedFilesByFolder
            ) }
            Log.d("SyncManager", "💾 Saved previously synced files")
        } catch (e: Exception) {
            Log.e("SyncManager", "❌ Failed to save sync state", e)
        }
    }

    fun loadPreviouslySyncedFiles() {
        previouslySyncedFilesByFolder.clear()
        if (!SYNC_STATE_FILE.exists()) return

        try {
            ObjectInputStream(SYNC_STATE_FILE.inputStream()).use {
                @Suppress("UNCHECKED_CAST")
                val savedFiles = it.readObject() as Map<String, Set<String>>
                previouslySyncedFilesByFolder.putAll(savedFiles.mapValues { it.value.toMutableSet() })
            }
            Log.d("SyncManager", "📤 Loaded previously synced files")
        } catch (e: Exception) {
            Log.e("SyncManager", "❌ Failed to load sync state", e)
        }
    }


    suspend fun getDriveFolderList(): Set<String> {
        return try {
            val driveFolders = GoogleDriveHelper.listFoldersInDrive(DRIVE_FOLDER_ID)
            driveFolders.map { it.name }.toSet()
        } catch (e: Exception) {
            Log.e("SyncManager", "Error fetching Drive folder list", e)
            emptySet()
        }
    }
    suspend fun deleteLocalFoldersIfRemovedInDrive() {
        val localFolders = File(LOCAL_FOLDER_PATH).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet() ?: emptySet()

        val driveFolders = getDriveFolderList()

        for (folderName in localFolders) {
            if (!driveFolders.contains(folderName)) {
                Log.d("SyncManager", "🚨 Deleting local folder (missing in Drive): $folderName")
                val folderToDelete = File(LOCAL_FOLDER_PATH, folderName)
                folderToDelete.deleteRecursively()  // ✅ Delete folder and contents
            }
        }
    }
    suspend fun deleteDriveFoldersIfRemovedLocally() {
        val localFolders = File(LOCAL_FOLDER_PATH).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet() ?: emptySet()

        val driveFolders = getDriveFolderList()

        for (folderName in driveFolders) {
            if (!localFolders.contains(folderName)) {
                Log.d("SyncManager", "🗑 Deleting Drive folder (missing locally): $folderName")
                val folderId = GoogleDriveHelper.findFolderInDrive(folderName, DRIVE_FOLDER_ID)
                if (folderId != null) {
                    GoogleDriveHelper.deleteFolder(folderId)
                }
            }
        }
    }



}
