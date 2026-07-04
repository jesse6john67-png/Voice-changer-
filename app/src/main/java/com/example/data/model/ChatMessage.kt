package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "user" or "assistant"
    val content: String,
    val isSuggestion: Boolean = false, // If true, can have a JSON suggestion payload
    val suggestionJson: String? = null, // Store suggested mix configurations
    val timestamp: Long = System.currentTimeMillis()
)
