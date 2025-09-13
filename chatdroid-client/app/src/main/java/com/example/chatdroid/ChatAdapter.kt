package com.example.chatdroid

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.messageText)
        val timestampText: TextView = view.findViewById(R.id.timestampText)
        private var isTimestampExpanded = false
        
        fun setTimestamp(timestamp: String?) {
            if (timestamp.isNullOrEmpty()) {
                timestampText.visibility = View.GONE
                return
            }
            
            timestampText.visibility = View.VISIBLE
            
            // Show only HH:mm:ss by default
            val shortTime = extractTimeFromTimestamp(timestamp)
            timestampText.text = shortTime
            
            // Set click listener for expand/collapse
            timestampText.setOnClickListener {
                isTimestampExpanded = !isTimestampExpanded
                if (isTimestampExpanded) {
                    timestampText.text = timestamp
                    timestampText.maxLines = 3
                } else {
                    timestampText.text = shortTime
                    timestampText.maxLines = 1
                }
            }
        }
        
        private fun extractTimeFromTimestamp(timestamp: String): String {
            Log.d("ChatDroid", "Extracting time from timestamp: '$timestamp'")
            
            return try {
                // First, always try to find time pattern HH:mm:ss anywhere in the string
                val timePattern = Regex("\\d{1,2}:\\d{2}:\\d{2}")
                val match = timePattern.find(timestamp)
                
                if (match != null) {
                    Log.d("ChatDroid", "Found time pattern: '${match.value}'")
                    return match.value
                }
                
                // Fallback parsing for different formats
                val result = when {
                    timestamp.contains("T") -> {
                        // ISO format like "2024-01-01T14:30:25"
                        val time = timestamp.substringAfter("T").substringBefore(".")
                        time.take(8)
                    }
                    timestamp.contains(" ") -> {
                        // Format like "13/09/2025 10:20:10"
                        val parts = timestamp.trim().split(" ")
                        Log.d("ChatDroid", "Split parts: ${parts.joinToString(", ")}")
                        if (parts.size >= 2) {
                            val timePart = parts[1]
                            Log.d("ChatDroid", "Time part: '$timePart'")
                            timePart
                        } else {
                            timestamp.take(8)
                        }
                    }
                    else -> timestamp.take(8)
                }
                
                Log.d("ChatDroid", "Extracted time result: '$result'")
                result
            } catch (e: Exception) {
                Log.e("ChatDroid", "Error extracting time: ${e.message}")
                timestamp.take(8)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.text
        holder.setTimestamp(message.timestamp)
    }

    override fun getItemCount() = messages.size
}