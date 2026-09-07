package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.DocumentChunkEntity
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<DocumentEntity>)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM documents")
    suspend fun deleteAllDocuments()

    // Chunks
    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks WHERE docId = :docId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocument(docId: String): List<DocumentChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<DocumentChunkEntity>)

    @Query("DELETE FROM document_chunks WHERE docId = :docId")
    suspend fun deleteChunksByDocId(docId: String)

    @Query("DELETE FROM document_chunks")
    suspend fun deleteAllChunks()

    @Query("SELECT COUNT(*) FROM document_chunks")
    suspend fun getChunkCount(): Int
}
