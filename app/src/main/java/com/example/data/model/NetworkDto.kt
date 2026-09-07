package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HealthResponseDto(
    val status: String,
    @Json(name = "uptime_seconds") val uptimeSeconds: Float,
    @Json(name = "vector_store") val vectorStore: VectorStoreStatsDto
)

@JsonClass(generateAdapter = true)
data class VectorStoreStatsDto(
    @Json(name = "total_documents") val totalDocuments: Int,
    @Json(name = "total_chunks") val totalChunks: Int,
    @Json(name = "vector_dimension") val vectorDimension: Int,
    val status: String
)

@JsonClass(generateAdapter = true)
data class SearchRequestDto(
    val query: String,
    @Json(name = "top_k") val topK: Int = 5,
    val threshold: Float = 0.15f,
    val category: String? = null
)

@JsonClass(generateAdapter = true)
data class ChunkHitDto(
    @Json(name = "chunk_id") val chunkId: String,
    @Json(name = "doc_id") val docId: String,
    @Json(name = "doc_title") val docTitle: String,
    val category: String,
    @Json(name = "chunk_index") val chunkIndex: Int,
    val text: String,
    @Json(name = "similarity_score") val similarityScore: Float,
    @Json(name = "match_percentage") val matchPercentage: Float,
    @Json(name = "vector_preview") val vectorPreview: List<Float>? = null
)

@JsonClass(generateAdapter = true)
data class SearchResponseDto(
    val query: String,
    @Json(name = "top_k") val topK: Int,
    val threshold: Float,
    @Json(name = "total_candidates") val totalCandidates: Int,
    @Json(name = "matched_count") val matchedCount: Int,
    @Json(name = "latency_ms") val latencyMs: Float,
    val results: List<ChunkHitDto>
)

@JsonClass(generateAdapter = true)
data class ChatRequestDto(
    val question: String,
    @Json(name = "top_k") val topK: Int = 3,
    val threshold: Float = 0.2f
)

@JsonClass(generateAdapter = true)
data class CitationDto(
    val rank: Int,
    @Json(name = "doc_title") val docTitle: String,
    @Json(name = "chunk_id") val chunkId: String,
    @Json(name = "chunk_index") val chunkIndex: Int,
    @Json(name = "similarity_score") val similarityScore: Float,
    @Json(name = "match_percentage") val matchPercentage: Float,
    val snippet: String
)

@JsonClass(generateAdapter = true)
data class ChatResponseDto(
    val question: String,
    val answer: String,
    val citations: List<CitationDto> = emptyList(),
    val confidence: Float = 0f,
    @Json(name = "latency_ms") val latencyMs: Float = 0f,
    @Json(name = "sources_consulted") val sourcesConsulted: Int = 0
)

@JsonClass(generateAdapter = true)
data class IngestRequestDto(
    val title: String,
    val content: String,
    val category: String = "General",
    val author: String = "Mobile User",
    @Json(name = "chunk_size") val chunkSize: Int = 400,
    @Json(name = "chunk_overlap") val chunkOverlap: Int = 80
)

@JsonClass(generateAdapter = true)
data class IngestResponseDto(
    val message: String,
    @Json(name = "document_id") val documentId: String,
    @Json(name = "chunk_count") val chunkCount: Int,
    @Json(name = "estimated_tokens") val estimatedTokens: Int
)
