package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val author: String,
    val createdAt: Long,
    val totalChars: Int,
    val totalTokens: Int,
    val chunkCount: Int,
    val content: String
)

@Entity(tableName = "document_chunks")
data class DocumentChunkEntity(
    @PrimaryKey val chunkId: String,
    val docId: String,
    val docTitle: String,
    val category: String,
    val chunkIndex: Int,
    val text: String,
    val charCount: Int,
    val estimatedTokens: Int,
    val embeddingVectorJson: String // Serialized float array of 64 dimensions
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "assistant"
    val text: String,
    val citationsJson: String = "[]", // Serialized citations list
    val confidence: Float = 0f,
    val searchLatencyMs: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class Citation(
    val rank: Int,
    val docTitle: String,
    val chunkId: String,
    val chunkIndex: Int,
    val similarityScore: Float,
    val matchPercentage: Float,
    val snippet: String
)

data class VectorSearchResult(
    val chunkId: String,
    val docId: String,
    val docTitle: String,
    val category: String,
    val chunkIndex: Int,
    val text: String,
    val similarityScore: Float,
    val matchPercentage: Float,
    val vectorPreview: List<Float> = emptyList()
)

data class ServerStatus(
    val isConnected: Boolean = false,
    val serverUrl: String = "http://10.0.2.2:8000",
    val latencyMs: Float = 0f,
    val totalDocs: Int = 0,
    val totalChunks: Int = 0,
    val vectorDimension: Int = 64,
    val statusMessage: String = "Not connected"
)
