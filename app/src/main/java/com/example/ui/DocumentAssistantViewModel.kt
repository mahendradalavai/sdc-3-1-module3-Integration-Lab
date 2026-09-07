package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.Citation
import com.example.data.model.DocumentChunkEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.ServerStatus
import com.example.data.model.VectorSearchResult
import com.example.data.repository.DocumentAssistantRepository
import com.example.data.repository.SearchMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val label: String) {
    VECTOR_SEARCH("Vector Search"),
    ASSISTANT_CHAT("Document Chat"),
    DOCUMENT_LIBRARY("Documents"),
    SERVER_DIAGNOSTICS("Lab Server")
}

data class UiState(
    val currentTab: AppTab = AppTab.VECTOR_SEARCH,
    val searchQuery: String = "Cosine similarity HNSW graph",
    val searchTopK: Int = 4,
    val searchThreshold: Float = 0.15f,
    val searchResults: List<VectorSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchMeta: SearchMeta? = null,
    val selectedResultChunk: VectorSearchResult? = null,
    // Chat state
    val chatInput: String = "",
    val isGeneratingAnswer: Boolean = false,
    val activeCitation: Citation? = null,
    // Document state
    val isIngesting: Boolean = false,
    val isIngestDialogOpen: Boolean = false,
    val selectedDocPreview: DocumentEntity? = null,
    val selectedDocChunks: List<DocumentChunkEntity> = emptyList(),
    // Server state
    val serverUrl: String = "http://10.0.2.2:8000",
    val serverStatus: ServerStatus = ServerStatus(),
    val isCheckingServer: Boolean = false,
    val useServerMode: Boolean = false,
    val snackbarMessage: String? = null
)

class DocumentAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = DocumentAssistantRepository(db.documentDao(), db.chatDao())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.initializeDefaultDocumentsIfEmpty()
            // Auto perform an initial search to populate vector results
            performVectorSearch()
            // Check server health in background
            checkServerHealth()
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchTopK(topK: Int) {
        _uiState.update { it.copy(searchTopK = topK) }
        performVectorSearch()
    }

    fun updateSearchThreshold(threshold: Float) {
        _uiState.update { it.copy(searchThreshold = threshold) }
        performVectorSearch()
    }

    fun performVectorSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val (results, meta) = repository.searchVectors(
                query = query,
                topK = _uiState.value.searchTopK,
                threshold = _uiState.value.searchThreshold,
                useServer = _uiState.value.useServerMode,
                serverUrl = _uiState.value.serverUrl
            )
            _uiState.update {
                it.copy(
                    searchResults = results,
                    searchMeta = meta,
                    isSearching = false
                )
            }
        }
    }

    fun setSelectedResultChunk(chunk: VectorSearchResult?) {
        _uiState.update { it.copy(selectedResultChunk = chunk) }
    }

    // Chat operations
    fun updateChatInput(text: String) {
        _uiState.update { it.copy(chatInput = text) }
    }

    fun sendChatMessage() {
        val question = _uiState.value.chatInput.trim()
        if (question.isEmpty() || _uiState.value.isGeneratingAnswer) return

        _uiState.update { it.copy(chatInput = "", isGeneratingAnswer = true) }
        viewModelScope.launch {
            try {
                repository.askAssistant(
                    question = question,
                    topK = 3,
                    threshold = _uiState.value.searchThreshold,
                    useServer = _uiState.value.useServerMode,
                    serverUrl = _uiState.value.serverUrl
                )
            } catch (e: Exception) {
                showSnackbar("Error answering question: ${e.localizedMessage}")
            } finally {
                _uiState.update { it.copy(isGeneratingAnswer = false) }
            }
        }
    }

    fun setActiveCitation(citation: Citation?) {
        _uiState.update { it.copy(activeCitation = citation) }
    }

    suspend fun parseCitations(json: String): List<Citation> {
        return repository.parseCitations(json)
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
            showSnackbar("Chat conversation cleared")
        }
    }

    // Document operations
    fun setIngestDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isIngestDialogOpen = isOpen) }
    }

    fun ingestDocument(
        title: String,
        content: String,
        category: String,
        author: String,
        chunkSize: Int,
        overlap: Int
    ) {
        if (title.isBlank() || content.isBlank()) {
            showSnackbar("Title and document content are required")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isIngesting = true) }
            try {
                val doc = repository.ingestLocalDocument(
                    title = title,
                    content = content,
                    category = category,
                    author = author,
                    chunkSize = chunkSize,
                    overlap = overlap
                )
                _uiState.update { it.copy(isIngestDialogOpen = false) }
                showSnackbar("Ingested '${doc.title}' into ${doc.chunkCount} vector chunks")
                // Re-run search
                performVectorSearch()
            } catch (e: Exception) {
                showSnackbar("Failed to ingest document: ${e.localizedMessage}")
            } finally {
                _uiState.update { it.copy(isIngesting = false) }
            }
        }
    }

    fun viewDocumentDetails(document: DocumentEntity) {
        viewModelScope.launch {
            val chunks = repository.getChunksForDocument(document.id)
            _uiState.update {
                it.copy(
                    selectedDocPreview = document,
                    selectedDocChunks = chunks
                )
            }
        }
    }

    fun closeDocumentPreview() {
        _uiState.update {
            it.copy(
                selectedDocPreview = null,
                selectedDocChunks = emptyList()
            )
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(docId)
            showSnackbar("Document deleted from vector index")
            performVectorSearch()
        }
    }

    fun resetToSampleData() {
        viewModelScope.launch {
            repository.seedDefaultLabDocuments()
            showSnackbar("Reset and loaded default integration lab documents")
            performVectorSearch()
        }
    }

    // Server operations
    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url) }
    }

    fun toggleServerMode(enabled: Boolean) {
        _uiState.update { it.copy(useServerMode = enabled) }
        if (enabled) {
            checkServerHealth()
        }
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingServer = true) }
            val status = repository.checkServerHealth(_uiState.value.serverUrl)
            _uiState.update {
                it.copy(
                    serverStatus = status,
                    isCheckingServer = false
                )
            }
        }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
