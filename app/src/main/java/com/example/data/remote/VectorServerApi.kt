package com.example.data.remote

import com.example.data.model.ChatRequestDto
import com.example.data.model.ChatResponseDto
import com.example.data.model.HealthResponseDto
import com.example.data.model.IngestRequestDto
import com.example.data.model.IngestResponseDto
import com.example.data.model.SearchRequestDto
import com.example.data.model.SearchResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface VectorServerApi {

    @GET("health")
    suspend fun checkHealth(): HealthResponseDto

    @POST("api/vector/search")
    suspend fun searchVectors(@Body request: SearchRequestDto): SearchResponseDto

    @POST("api/chat/rag")
    suspend fun chatRag(@Body request: ChatRequestDto): ChatResponseDto

    @POST("api/documents/ingest")
    suspend fun ingestDocument(@Body request: IngestRequestDto): IngestResponseDto

    @POST("api/sample-data/seed")
    suspend fun reseedSampleData(): Map<String, Any>
}
