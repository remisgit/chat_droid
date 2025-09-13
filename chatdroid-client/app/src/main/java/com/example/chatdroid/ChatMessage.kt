package com.example.chatdroid

data class ChatMessage(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: String? = null
)