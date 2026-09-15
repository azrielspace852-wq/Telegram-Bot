package com.offlineai.app.data

import java.util.UUID

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class Attachment(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String? = null,   // for text/pdf/zip
    val isImage: Boolean = false,
    val isAudio: Boolean = false,
    val isVideo: Boolean = false,
    val isZip: Boolean = false
)
