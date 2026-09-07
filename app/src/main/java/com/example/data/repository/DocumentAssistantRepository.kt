package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.local.DocumentDao
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ChatRequestDto
import com.example.data.model.Citation
import com.example.data.model.DocumentChunkEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.IngestRequestDto
import com.example.data.model.SearchRequestDto
import com.example.data.model.ServerStatus
import com.example.data.model.VectorSearchResult
import com.example.data.remote.ApiClient
import com.example.data.vector.DenseEmbeddingGenerator
import com.example.data.vector.TextChunker
import com.example.data.vector.VectorMath
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class DocumentAssistantRepository(
    private val documentDao: DocumentDao,
    private val chatDao: ChatDao
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val chatMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessages()

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val citationListType = Types.newParameterizedType(List::class.java, Citation::class.java)
    private val citationAdapter = moshi.adapter<List<Citation>>(citationListType)

    suspend fun initializeDefaultDocumentsIfEmpty() = withContext(Dispatchers.IO) {
        val count = documentDao.getChunkCount()
        if (count == 0) {
            seedDefaultLabDocuments()
        }
    }

    suspend fun seedDefaultLabDocuments() = withContext(Dispatchers.IO) {
        val sampleDocs = listOf(
            SampleDoc(
                title = "Vector Databases and Approximate Nearest Neighbor (ANN)",
                category = "Vector Search",
                author = "AI Research Lab",
                content = """Vector databases index high-dimensional embeddings using algorithms like HNSW (Hierarchical Navigable Small World) and IVF-PQ (Inverted File with Product Quantization). 
HNSW constructs a multi-layer graph where upper layers feature long-range connections for fast greedy routing, and lower layers offer granular local connectivity. This enables logarithmic search complexity O(log N).

Product Quantization compresses continuous vectors into compact byte codes, enabling billions of vectors to reside in system memory. 
Cosine similarity measures the angle between two unit-normalized vectors:
cos(u, v) = (u . v) / (||u|| * ||v||).
When vectors are L2-normalized, cosine similarity equals the dot product, allowing SIMD hardware acceleration.

In contrast, Euclidean distance measures geometric straight-line separation. Cosine similarity is preferred in Natural Language Processing because it assesses semantic orientation irrespective of document token length."""
            ),
            SampleDoc(
                title = "Retrieval-Augmented Generation (RAG) Architecture",
                category = "System Architecture",
                author = "Systems Engineering",
                content = """Retrieval-Augmented Generation mitigates hallucination in Large Language Models by grounding outputs in retrieved domain knowledge.
The RAG pipeline operates in three discrete stages:
1. Ingestion: Documents are extracted, cleaned, chunked into optimal semantic windows (256 to 512 tokens), and embedded into a vector space.
2. Retrieval: When a query arrives, it is embedded using the same vectorizer. Top-K nearest neighbors are retrieved based on cosine similarity thresholds.
3. Generation: A grounded system prompt combines the user question with the retrieved chunks as authoritative context.

Advanced RAG techniques incorporate re-ranking using Cross-Encoders, hybrid BM25 + dense vector search, and recursive hierarchical chunking. Citation attribution ensures every factual claim maps back to verifiable source documents."""
            ),
            SampleDoc(
                title = "Edge AI and On-Device Embedding Inference",
                category = "Mobile Systems",
                author = "Mobile Core Team",
                content = """Deploying vector search on mobile clients requires careful memory budget and battery optimization.
Mobile SQLite with Room database can store chunk representations alongside quantized embedding vectors. 
By storing normalized float arrays or int8 quantized embeddings, mobile devices can execute cosine similarity scans across thousands of document chunks in sub-10ms latency using Kotlin coroutines and SIMD acceleration.

Hybrid mobile architectures utilize local on-device caches for instant offline retrieval while synchronizing with Python backend inference servers for computationally intensive re-ranking or global multi-tenant vector searches.
Network failover strategies ensure the mobile document assistant seamlessly falls back to on-device cosine matching when offline."""
            ),
            SampleDoc(
                title = "Zero-Trust API Security in Integration Labs",
                category = "Security & Infra",
                author = "Security Ops",
                content = """Microservice architectures connecting mobile frontends to AI model backends must enforce strict zero-trust standards.
Every endpoint requires mTLS or signed JWT authentication. Rate limiting via token-bucket algorithms prevents denial-of-service vector search spikes.
Embedding generation endpoints should implement request batching to saturate GPU vector engines efficiently without causing thread starvation.

Sanitization filters must check ingested documents for prompt injection attacks and malicious markdown payloads before chunks enter the vector indexing pipeline."""
            )
        )

        for (doc in sampleDocs) {
            ingestLocalDocument(
                title = doc.title,
                content = doc.content,
                category = doc.category,
                author = doc.author,
                chunkSize = 400,
                overlap = 80
            )
        }
    }

    suspend fun ingestLocalDocument(
        title: String,
        content: String,
        category: String,
        author: String,
        chunkSize: Int = 400,
        overlap: Int = 80
    ): DocumentEntity = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString().take(8)
        val textChunks = TextChunker.chunkText(content, chunkSize, overlap)
        val totalTokens = textChunks.sumOf { it.estimatedTokens }

        val chunkEntities = textChunks.map { c ->
            val vector = DenseEmbeddingGenerator.embedText(c.text)
            DocumentChunkEntity(
                chunkId = "${docId}_${c.chunkIndex}",
                docId = docId,
                docTitle = title,
                category = category,
                chunkIndex = c.chunkIndex,
                text = c.text,
                charCount = c.charCount,
                estimatedTokens = c.estimatedTokens,
                embeddingVectorJson = DenseEmbeddingGenerator.floatArrayToJson(vector)
            )
        }

        val docEntity = DocumentEntity(
            id = docId,
            title = title,
            category = category,
            author = author,
            createdAt = System.currentTimeMillis(),
            totalChars = content.length,
            totalTokens = totalTokens,
            chunkCount = chunkEntities.size,
            content = content
        )

        documentDao.insertDocument(docEntity)
        documentDao.insertChunks(chunkEntities)
        docEntity
    }

    suspend fun searchVectors(
        query: String,
        topK: Int = 5,
        threshold: Float = 0.15f,
        useServer: Boolean = false,
        serverUrl: String = "http://10.0.2.2:8000"
    ): Pair<List<VectorSearchResult>, SearchMeta> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (useServer) {
            try {
                val api = ApiClient.getApi(serverUrl)
                val response = api.searchVectors(
                    SearchRequestDto(query = query, topK = topK, threshold = threshold)
                )
                val results = response.results.map {
                    VectorSearchResult(
                        chunkId = it.chunkId,
                        docId = it.docId,
                        docTitle = it.docTitle,
                        category = it.category,
                        chunkIndex = it.chunkIndex,
                        text = it.text,
                        similarityScore = it.similarityScore,
                        matchPercentage = it.matchPercentage,
                        vectorPreview = it.vectorPreview ?: emptyList()
                    )
                }
                return@withContext Pair(
                    results,
                    SearchMeta(
                        latencyMs = response.latencyMs,
                        totalCandidates = response.totalCandidates,
                        source = "Python Server API"
                    )
                )
            } catch (e: Exception) {
                // Graceful fallback to local engine
            }
        }

        // Local On-Device Vector Search
        val queryVector = DenseEmbeddingGenerator.embedText(query)
        val allChunks = documentDao.getAllChunks()

        val scored = allChunks.mapNotNull { chunk ->
            val chunkVec = DenseEmbeddingGenerator.jsonToFloatArray(chunk.embeddingVectorJson)
            val sim = VectorMath.cosineSimilarity(queryVector, chunkVec)
            if (sim >= threshold) {
                VectorSearchResult(
                    chunkId = chunk.chunkId,
                    docId = chunk.docId,
                    docTitle = chunk.docTitle,
                    category = chunk.category,
                    chunkIndex = chunk.chunkIndex,
                    text = chunk.text,
                    similarityScore = (Math.round(sim * 10000f) / 10000f),
                    matchPercentage = (Math.round(maxOf(0f, sim) * 1000f) / 10f),
                    vectorPreview = chunkVec.take(4)
                )
            } else null
        }.sortedByDescending { it.similarityScore }.take(topK)

        val elapsed = (System.currentTimeMillis() - startTime).toFloat()
        Pair(
            scored,
            SearchMeta(
                latencyMs = elapsed,
                totalCandidates = allChunks.size,
                source = "On-Device Vector Engine"
            )
        )
    }

    suspend fun askAssistant(
        question: String,
        topK: Int = 3,
        threshold: Float = 0.2f,
        useServer: Boolean = false,
        serverUrl: String = "http://10.0.2.2:8000"
    ): ChatMessageEntity = withContext(Dispatchers.IO) {
        // Record user message
        chatDao.insertMessage(
            ChatMessageEntity(
                role = "user",
                text = question
            )
        )

        val startTime = System.currentTimeMillis()

        if (useServer) {
            try {
                val api = ApiClient.getApi(serverUrl)
                val response = api.chatRag(
                    ChatRequestDto(question = question, topK = topK, threshold = threshold)
                )
                val citations = response.citations.map {
                    Citation(
                        rank = it.rank,
                        docTitle = it.docTitle,
                        chunkId = it.chunkId,
                        chunkIndex = it.chunkIndex,
                        similarityScore = it.similarityScore,
                        matchPercentage = it.matchPercentage,
                        snippet = it.snippet
                    )
                }
                val assistantMsg = ChatMessageEntity(
                    role = "assistant",
                    text = response.answer,
                    citationsJson = citationAdapter.toJson(citations) ?: "[]",
                    confidence = response.confidence,
                    searchLatencyMs = response.latencyMs
                )
                chatDao.insertMessage(assistantMsg)
                return@withContext assistantMsg
            } catch (e: Exception) {
                // Fallback to local RAG
            }
        }

        // Local RAG execution
        val (hits, meta) = searchVectors(
            query = question,
            topK = topK,
            threshold = threshold,
            useServer = false
        )

        val assistantMsg: ChatMessageEntity = if (hits.isEmpty()) {
            ChatMessageEntity(
                role = "assistant",
                text = "No relevant passages were found in your local indexed documents matching '$question'. Try lowering the similarity threshold or adding more documents.",
                citationsJson = "[]",
                confidence = 0f,
                searchLatencyMs = meta.latencyMs
            )
        } else {
            val citations = hits.mapIndexed { idx, hit ->
                Citation(
                    rank = idx + 1,
                    docTitle = hit.docTitle,
                    chunkId = hit.chunkId,
                    chunkIndex = hit.chunkIndex,
                    similarityScore = hit.similarityScore,
                    matchPercentage = hit.matchPercentage,
                    snippet = if (hit.text.length > 140) hit.text.take(140) + "..." else hit.text
                )
            }

            val topHit = hits.first()
            val avgScore = hits.map { it.similarityScore }.average().toFloat()
            val confidence = Math.round(avgScore * 1000f) / 10f

            val synthesizedText = buildString {
                append("Based on the indexed document '${topHit.docTitle}' (Match: ${topHit.matchPercentage}%):\n\n")
                append(topHit.text)
                if (hits.size > 1) {
                    append("\n\nRelated context from '${hits[1].docTitle}':\n")
                    val secSentence = hits[1].text.split(". ").firstOrNull() ?: hits[1].text.take(100)
                    append("• $secSentence.")
                }
            }

            ChatMessageEntity(
                role = "assistant",
                text = synthesizedText,
                citationsJson = citationAdapter.toJson(citations) ?: "[]",
                confidence = confidence,
                searchLatencyMs = meta.latencyMs
            )
        }

        chatDao.insertMessage(assistantMsg)
        assistantMsg
    }

    suspend fun checkServerHealth(serverUrl: String): ServerStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val api = ApiClient.getApi(serverUrl)
            val health = api.checkHealth()
            val latency = (System.currentTimeMillis() - start).toFloat()
            ServerStatus(
                isConnected = true,
                serverUrl = serverUrl,
                latencyMs = latency,
                totalDocs = health.vectorStore.totalDocuments,
                totalChunks = health.vectorStore.totalChunks,
                vectorDimension = health.vectorStore.vectorDimension,
                statusMessage = "Server Online (${health.status})"
            )
        } catch (e: Exception) {
            val latency = (System.currentTimeMillis() - start).toFloat()
            ServerStatus(
                isConnected = false,
                serverUrl = serverUrl,
                latencyMs = latency,
                statusMessage = "Connection failed: ${e.localizedMessage ?: "Unknown host"}"
            )
        }
    }

    suspend fun deleteDocument(docId: String) = withContext(Dispatchers.IO) {
        documentDao.deleteDocumentById(docId)
        documentDao.deleteChunksByDocId(docId)
    }

    suspend fun clearChat() = withContext(Dispatchers.IO) {
        chatDao.clearChat()
    }

    suspend fun getChunksForDocument(docId: String): List<DocumentChunkEntity> = withContext(Dispatchers.IO) {
        documentDao.getChunksForDocument(docId)
    }

    suspend fun parseCitations(json: String): List<Citation> {
        return try {
            citationAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class SearchMeta(
    val latencyMs: Float,
    val totalCandidates: Int,
    val source: String
)

private data class SampleDoc(
    val title: String,
    val category: String,
    val author: String,
    val content: String
)
