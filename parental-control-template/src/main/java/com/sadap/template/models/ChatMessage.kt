package com.sadap.template.models

import java.time.LocalDateTime

data class ChatMessage(
    val deviceId: String,
    val sender: String, // "anak" atau "ortu"
    val message: String,
    val timestamp: String // ISO 8601 string
)
