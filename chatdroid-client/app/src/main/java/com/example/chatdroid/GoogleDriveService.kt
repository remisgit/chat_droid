package com.example.chatdroid

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class GoogleDriveService(private val context: Context) {
    
    private var googleSignInClient: GoogleSignInClient
    private var driveService: Drive? = null
    private var sheetsService: Sheets? = null
    
    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY), Scope(SheetsScopes.SPREADSHEETS_READONLY))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }
    
    fun handleSignInResult(account: GoogleSignInAccount?): Boolean {
        return if (account != null) {
            initializeDriveService(account)
            true
        } else {
            false
        }
    }
    
    private fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, 
            listOf(DriveScopes.DRIVE_READONLY, SheetsScopes.SPREADSHEETS_READONLY)
        )
        credential.selectedAccount = account.account
        
        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ChatDroid")
            .build()
            
        sheetsService = Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("ChatDroid")
            .build()
    }
    
    suspend fun listFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<DriveFile>()
        try {
            val result = driveService?.files()?.list()
                ?.setPageSize(10)
                ?.setFields("nextPageToken, files(id, name, mimeType, modifiedTime)")
                ?.execute()
            
            result?.files?.forEach { file ->
                files.add(
                    DriveFile(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        modifiedTime = file.modifiedTime?.toString() ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        files
    }
    
    suspend fun listRootFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<DriveFile>()
        Log.d("ChatDroid", "Listing all files and folders in root directory")
        
        val result = driveService?.files()?.list()
            ?.setQ("parents in 'root'")
            ?.setFields("files(id, name, mimeType)")
            ?.execute()
        
        result?.files?.forEach { file ->
            files.add(
                DriveFile(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType ?: "",
                    modifiedTime = ""
                )
            )
            Log.d("ChatDroid", "Root item: ${file.name} (${file.mimeType}) ID: ${file.id}")
        }
        
        Log.d("ChatDroid", "Total items in root: ${files.size}")
        files
    }

    suspend fun findFileInPath(path: String, fileName: String): String? = withContext(Dispatchers.IO) {
        Log.d("ChatDroid", "Starting file search - Path: '$path', File: '$fileName'")
        
        // First list what's actually in root
        listRootFiles()
        
        // First find the folders in the path
        val pathParts = path.split("/").filter { it.isNotEmpty() }
        var currentFolderId = "root"
        
        Log.d("ChatDroid", "Path parts: ${pathParts.joinToString(" -> ")}")
        
        // Navigate through the folder structure
        for (folderName in pathParts) {
            Log.d("ChatDroid", "Searching for folder: '$folderName' in parent: '$currentFolderId'")
            
            val folderQuery = "name='$folderName' and parents in '$currentFolderId' and mimeType='application/vnd.google-apps.folder'"
            Log.d("ChatDroid", "Folder query: $folderQuery")
            
            val folderResult = driveService?.files()?.list()
                ?.setQ(folderQuery)
                ?.setFields("files(id, name)")
                ?.execute()
            
            Log.d("ChatDroid", "Found ${folderResult?.files?.size ?: 0} folders matching '$folderName'")
            folderResult?.files?.forEach { folder ->
                Log.d("ChatDroid", "  - Folder: ${folder.name} (ID: ${folder.id})")
            }
            
            val folder = folderResult?.files?.firstOrNull()
            if (folder != null) {
                currentFolderId = folder.id
                Log.d("ChatDroid", "Moving to folder: ${folder.name} (ID: ${folder.id})")
            } else {
                Log.e("ChatDroid", "Folder '$folderName' not found in path!")
                return@withContext null // Folder not found
            }
        }
        
        // Now search for the file in the final folder
        Log.d("ChatDroid", "Searching for file: '$fileName' in folder ID: '$currentFolderId'")
        
        val fileQuery = "name='$fileName' and parents in '$currentFolderId'"
        Log.d("ChatDroid", "File query: $fileQuery")
        
        val fileResult = driveService?.files()?.list()
            ?.setQ(fileQuery)
            ?.setFields("files(id, name)")
            ?.execute()
        
        Log.d("ChatDroid", "Found ${fileResult?.files?.size ?: 0} files matching '$fileName'")
        fileResult?.files?.forEach { file ->
            Log.d("ChatDroid", "  - File: ${file.name} (ID: ${file.id})")
        }
        
        val foundFile = fileResult?.files?.firstOrNull()
        if (foundFile != null) {
            Log.d("ChatDroid", "File found! ID: ${foundFile.id}")
        } else {
            Log.e("ChatDroid", "File '$fileName' not found in final folder!")
        }
        
        foundFile?.id
    }
    
    suspend fun loadSheetsContent(fileId: String): List<SheetContent> = withContext(Dispatchers.IO) {
        val contentList = mutableListOf<SheetContent>()
        try {
            // Get all sheet names first
            val spreadsheet = sheetsService?.spreadsheets()?.get(fileId)?.execute()
            val sheetName = spreadsheet?.sheets?.firstOrNull()?.properties?.title ?: "Sheet1"
            
            // Read the data from the sheet
            val range = "$sheetName!A:Z" // Read all columns
            val response = sheetsService?.spreadsheets()?.values()
                ?.get(fileId, range)
                ?.execute()
            
            val values = response?.getValues()
            if (values != null && values.size > 1) {
                // Skip the first row (header) and find CONTENT and TIMESTAMP columns
                val headerRow = values[0]
                val contentColumnIndex = headerRow.indexOfFirst { 
                    it.toString().uppercase() == "CONTENT" 
                }
                val timestampColumnIndex = headerRow.indexOfFirst { 
                    it.toString().uppercase() == "TIMESTAMP" 
                }
                
                Log.d("ChatDroid", "Column indices - CONTENT: $contentColumnIndex, TIMESTAMP: $timestampColumnIndex")
                
                if (contentColumnIndex >= 0) {
                    // Extract content and timestamp, skipping header row
                    for (i in 1 until values.size) {
                        val row = values[i]
                        if (contentColumnIndex < row.size) {
                            val content = row[contentColumnIndex]?.toString()
                            val timestamp = if (timestampColumnIndex >= 0 && timestampColumnIndex < row.size) {
                                row[timestampColumnIndex]?.toString()
                            } else {
                                null
                            }
                            
                            if (!content.isNullOrEmpty()) {
                                contentList.add(SheetContent(content, timestamp))
                                Log.d("ChatDroid", "Added content: '$content' with timestamp: '$timestamp'")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        contentList
    }
    
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null && driveService != null
    }
    
    fun signOut() {
        googleSignInClient.signOut()
        driveService = null
        sheetsService = null
    }
}

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val modifiedTime: String
)

data class SheetContent(
    val content: String,
    val timestamp: String?
)