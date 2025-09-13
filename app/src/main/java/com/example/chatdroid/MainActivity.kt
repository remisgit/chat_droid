package com.example.chatdroid

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var driveButton: Button
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var driveService: GoogleDriveService
    private val messages = mutableListOf<ChatMessage>()

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d("ChatDroid", "Sign-in successful for account: ${account?.email}")
            if (driveService.handleSignInResult(account)) {
                Toast.makeText(this, "Google Drive connected!", Toast.LENGTH_SHORT).show()
                driveButton.text = "Reload Chat Data"
                loadChatDataFromSheets()
            }
        } catch (e: ApiException) {
            Log.e("ChatDroid", "Sign-in failed with error code: ${e.statusCode}, message: ${e.message}")
            Toast.makeText(this, "Sign-in failed: ${e.statusCode} - ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("ChatDroid", "Consent result: ${result.resultCode}")
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Permissions granted! Loading chat data...", Toast.LENGTH_SHORT).show()
            loadChatDataFromSheets()
        } else {
            Toast.makeText(this, "Permissions denied. Cannot access Google Drive.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        driveButton = findViewById(R.id.driveButton)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)

        driveService = GoogleDriveService(this)
        chatAdapter = ChatAdapter(messages)
        chatRecyclerView.adapter = chatAdapter
        chatRecyclerView.layoutManager = LinearLayoutManager(this)

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                val message = ChatMessage(messageText, true)
                messages.add(message)
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)
                messageInput.text.clear()
            }
        }

        driveButton.setOnClickListener {
            if (driveService.isSignedIn()) {
                loadChatDataFromSheets()
            } else {
                signInToGoogleDrive()
            }
        }
        
        // Auto-connect on launch
        checkAutoConnect()
    }

    private fun signInToGoogleDrive() {
        val signInIntent = driveService.getSignInIntent()
        signInLauncher.launch(signInIntent)
    }

    private fun checkAutoConnect() {
        // Check if user is already signed in
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(this)
        Log.d("ChatDroid", "Checking auto-connect. Last signed in account: ${lastSignedInAccount?.email}")
        if (lastSignedInAccount != null) {
            if (driveService.handleSignInResult(lastSignedInAccount)) {
                driveButton.text = "Reload Chat Data"
                Log.d("ChatDroid", "Auto-connect successful, loading chat data")
                loadChatDataFromSheets()
            }
        } else {
            Log.d("ChatDroid", "No previous sign-in found")
            driveButton.text = "Connect to Google Drive"
        }
    }
    
    private fun loadChatDataFromSheets() {
        lifecycleScope.launch {
            try {
                // Clear existing messages
                messages.clear()
                chatAdapter.notifyDataSetChanged()
                
                // Log current account
                val currentAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                Log.d("ChatDroid", "Loading chat data for account: ${currentAccount?.email}")
                Log.d("ChatDroid", "Searching for file 'Chat' in path: /dev/CHATDROID/")
                
                // Add loading message
                val loadingMessage = ChatMessage("Loading chat data from Google Sheets...", false)
                messages.add(loadingMessage)
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)
                
                // Find the Chat file in /dev/CHATDROID/ folder
                val fileId = driveService.findFileInPath("dev/CHATDROID", "Chat")
                Log.d("ChatDroid", "File search result - File ID: $fileId")
                
                if (fileId != null) {
                    // Load content from the CONTENT column
                    val contentList = driveService.loadSheetsContent(fileId)
                    
                    // Remove loading message
                    messages.removeAt(messages.size - 1)
                    chatAdapter.notifyItemRemoved(messages.size)
                    
                    if (contentList.isNotEmpty()) {
                        // Add each content item as a chat message
                        contentList.forEach { content ->
                            val message = ChatMessage(content, false)
                            messages.add(message)
                            chatAdapter.notifyItemInserted(messages.size - 1)
                        }
                        chatRecyclerView.scrollToPosition(messages.size - 1)
                        Toast.makeText(this@MainActivity, "Loaded ${contentList.size} messages from Google Sheets", Toast.LENGTH_SHORT).show()
                    } else {
                        val noContentMessage = ChatMessage("No content found in CONTENT column", false)
                        messages.add(noContentMessage)
                        chatAdapter.notifyItemInserted(messages.size - 1)
                    }
                } else {
                    // Remove loading message
                    messages.removeAt(messages.size - 1)
                    chatAdapter.notifyItemRemoved(messages.size)
                    
                    val errorMessage = ChatMessage("Chat file not found in /dev/CHATDROID/ folder", false)
                    messages.add(errorMessage)
                    chatAdapter.notifyItemInserted(messages.size - 1)
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.d("ChatDroid", "User consent required for Drive access")
                Toast.makeText(this@MainActivity, "Additional permissions needed...", Toast.LENGTH_SHORT).show()
                consentLauncher.launch(e.intent)
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading chat data: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }
}