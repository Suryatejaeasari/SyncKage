package com.android.system.update



import com.google.auth.http.HttpCredentialsAdapter
import android.util.Log
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.io.File as LocalFile

object GoogleDriveHelper {
     private const val SERVICE_ACCOUNT_JSON = """
    {
 "Content of JSON File"
}

    """

    private val transport: HttpTransport = NetHttpTransport()
    private val jsonFactory: JsonFactory = GsonFactory.getDefaultInstance()

    private val driveService: Drive by lazy {
        try {
            val credentials = ServiceAccountCredentials
                .fromStream(ByteArrayInputStream(SERVICE_ACCOUNT_JSON.toByteArray(StandardCharsets.UTF_8)))
                .createScoped(listOf("https://www.googleapis.com/auth/drive"))

            Drive.Builder(transport, jsonFactory, HttpCredentialsAdapter(credentials))
                .setApplicationName("EstSyncService")
                .build()
        } catch (e: Exception) {
            throw RuntimeException("Google Drive authentication failed: ${e.message}", e)
        }
    }

    suspend fun uploadFileToDrive(localFile: LocalFile, folderId: String) {
        withContext(Dispatchers.IO) {
            try {
                val existingFiles = listFilesInFolder(folderId) // 🔍 Get list of files in Drive

                val existingFile = existingFiles.find { it.name == localFile.name } // 🆕 Find matching file

                if (existingFile != null) {
                    val driveLastModified = getDriveFileModifiedTime(existingFile.id) ?: 0L
                    Log.d("GoogleDriveHelper", "📁 ${localFile.name} already exists in Drive (Drive: $driveLastModified, Local: ${localFile.lastModified()})")

                    // ✅ Only upload if the local file is newer
                    if (localFile.lastModified() > driveLastModified) {
                        Log.d("GoogleDriveHelper", "⬆️ Uploading updated file: ${localFile.name}")
                        performUpload(localFile, existingFile.id) // 🔄 Replace existing file
                    } else {
                        Log.d("GoogleDriveHelper", "⚡ No need to upload: ${localFile.name} (Local file is older or same)")
                    }
                } else {
                    Log.d("GoogleDriveHelper", "🆕 File does not exist in Drive, uploading now: ${localFile.name}")
                    performUpload(localFile, null) // 🔄 Upload new file
                }
            } catch (e: Exception) {
                Log.e("GoogleDriveHelper", "❌ Error uploading file: ${localFile.name}", e)
            }
        }
    }

    private fun performUpload(localFile: LocalFile, existingFileId: String?) {
        try {
            val fileMetadata = File().apply {
                name = localFile.name
                val DRIVE_FOLDER_ID = "drive_folder_id"
                parents = listOf(DRIVE_FOLDER_ID)
            }

            val mediaContent = FileContent("application/octet-stream", localFile)

            if (existingFileId != null) {
                // 🔄 Update existing file
                driveService.files().update(existingFileId, fileMetadata, mediaContent).execute()
                Log.d("GoogleDriveHelper", "✅ Updated: ${localFile.name}")
            } else {
                // 🆕 Upload new file
                driveService.files().create(fileMetadata, mediaContent).execute()
                Log.d("GoogleDriveHelper", "✅ Uploaded: ${localFile.name}")
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "❌ Failed to upload: ${localFile.name}", e)
        }
    }


    suspend fun listFilesInFolder(folderId: String): List<File> {
        return withContext(Dispatchers.IO) {  // ✅ Runs network call in background
            try {
                Log.d("GoogleDriveHelper", "📡 Fetching files from Drive folder: $folderId")
                val result: FileList = driveService.files().list()
                    .setQ("'$folderId' in parents and trashed=false")
                    .setFields("files(id, name, modifiedTime)")
                    .execute()

                Log.d("GoogleDriveHelper", "📂 Retrieved ${result.files.size} files from Drive.")
                result.files ?: emptyList()
            } catch (e: Exception) {
                Log.e("GoogleDriveHelper", "❌ Error listing files", e)
                emptyList()
            }
        }
    }





    fun findFileInDrive(fileName: String, folderId: String): String? {
        return try {
            val query = "name='$fileName' and '$folderId' in parents and trashed=false"
            val result = driveService.files().list().setQ(query).setFields("files(id)").execute()
            result.files.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Error finding file", e)
            null
        }
    }

    fun getDriveFileModifiedTime(fileId: String): Long? {
        return try {
            val file = driveService.files().get(fileId).setFields("modifiedTime").execute()
            file.modifiedTime?.value // Convert to milliseconds
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Error fetching modified time", e)
            null
        }
    }

    fun deleteFile(fileId: String): Boolean {
        return try {
            driveService.files().delete(fileId).execute()
            Log.d("GoogleDriveHelper", "File deleted: $fileId")
            true
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Error deleting file", e)
            false
        }
    }


    fun uploadFile(localFile: LocalFile, driveFolderId: String): String? {
        return try {
            val fileMetadata = File().apply {
                name = localFile.name
                parents = listOf(driveFolderId)
            }

            // Detect correct MIME type
            val mimeType = Files.probeContentType(Paths.get(localFile.absolutePath))
                ?: "application/octet-stream"

            val mediaContent = FileContent(mimeType, localFile)
            val uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            uploadedFile.id
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Upload failed", e)
            null
        }
    }

    fun downloadFile(fileId: String, destination: LocalFile): Boolean {
        return try {
            // 🔍 Step 1: Check if it's a folder
            val file = driveService.files().get(fileId).setFields("mimeType").execute()
            if (file.mimeType == "application/vnd.google-apps.folder") {
                Log.d("GoogleDriveHelper", "❌ Skipping download. It's a folder: ${fileId}")
                return false
            }

            // ✅ Step 2: Proceed with file download
            destination.parentFile?.mkdirs()

            FileOutputStream(destination).use { outputStream ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            }
            Log.d("GoogleDriveHelper", "✅ File downloaded: ${destination.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e("GoogleDriveHelper", "❌ Download failed", e)
            false
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "❌ Unexpected error during download", e)
            false
        }
    }

    fun createFolder(folderName: String, parentFolderId: String): String? {
        return try {
            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentFolderId)
            }

            val createdFolder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute()

            Log.d("GoogleDriveHelper", "📁 Folder created: $folderName (ID: ${createdFolder.id})")
            createdFolder.id
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "❌ Error creating folder: $folderName", e)
            null
        }
    }

    fun findFolderInDrive(folderName: String, parentFolderId: String): String? {
        return try {
            val query = "name='$folderName' and '$parentFolderId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false"
            val result = driveService.files().list().setQ(query).setFields("files(id)").execute()
            result.files.firstOrNull()?.id
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "❌ Error finding folder: $folderName", e)
            null
        }
    }

    fun getFileIdInFolder(folderId: String, fileName: String): String? {
        return try {
            val result = driveService.files().list()
                .setQ("'$folderId' in parents and name='$fileName' and trashed=false")
                .setFields("files(id)")
                .execute()

            result.files.firstOrNull()?.id  // ✅ Return the first matching file ID (or null if not found)
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "❌ Error getting file ID for $fileName", e)
            null
        }
    }


    suspend fun listFoldersInDrive(parentFolderId: String): List<File> {
        return withContext(Dispatchers.IO) {
            try {
                val result = driveService.files().list()
                    .setQ("'$parentFolderId' in parents and mimeType='application/vnd.google-apps.folder' and trashed=false")
                    .setFields("files(id, name)")
                    .execute()
                result.files ?: emptyList()
            } catch (e: Exception) {
                Log.e("GoogleDriveHelper", "❌ Error listing folders in Drive", e)
                emptyList()
            }
        }
    }

    suspend fun deleteFolder(folderId: String): Boolean {
        try {
            // First, list all files inside the folder
            val filesInFolder = listFilesInFolder(folderId)  // Implement this method

            // Delete all files inside the folder
            for (file in filesInFolder) {
                deleteFile(file.id)
            }

            // Now delete the folder
            driveService.files().delete(folderId).execute()
            return true
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "❌ Failed to delete folder: ${e.message}")
            return false
        }
    }



}
