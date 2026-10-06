package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppTab
import com.example.ui.DocumentAssistantViewModel
import com.example.ui.screens.ChatAssistantScreen
import com.example.ui.screens.DocumentLibraryScreen
import com.example.ui.screens.ServerDiagnosticsScreen
import com.example.ui.screens.VectorSearchScreen
import com.example.ui.theme.DocVectorTheme
import com.example.ui.theme.VectorMatchHigh

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DocVectorTheme {
                DocumentAssistantApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentAssistantApp(
    viewModel: DocumentAssistantViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DataObject,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DocVector Lab",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                },
                actions = {
                    // Active engine indicator chip
                    Surface(
                        modifier = Modifier.padding(end = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (uiState.useServerMode)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.useServerMode)
                                            (if (uiState.serverStatus.isConnected) VectorMatchHigh else MaterialTheme.colorScheme.primary)
                                        else
                                            MaterialTheme.colorScheme.secondary
                                    )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.useServerMode) "Server API" else "On-Device",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = if (uiState.useServerMode)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.VECTOR_SEARCH,
                    onClick = { viewModel.selectTab(AppTab.VECTOR_SEARCH) },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Vector Search") },
                    label = { Text("Search", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_vector_search"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.ASSISTANT_CHAT,
                    onClick = { viewModel.selectTab(AppTab.ASSISTANT_CHAT) },
                    icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat Assistant") },
                    label = { Text("Assistant", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_chat_assistant"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.DOCUMENT_LIBRARY,
                    onClick = { viewModel.selectTab(AppTab.DOCUMENT_LIBRARY) },
                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Document Corpus") },
                    label = { Text("Documents", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_documents"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = uiState.currentTab == AppTab.SERVER_DIAGNOSTICS,
                    onClick = { viewModel.selectTab(AppTab.SERVER_DIAGNOSTICS) },
                    icon = { Icon(Icons.Default.Dns, contentDescription = "Server Diagnostics") },
                    label = { Text("Lab Server", fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_server_diagnostics"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = uiState.currentTab,
            label = "ScreenTransition",
            modifier = Modifier.padding(innerPadding)
        ) { tab ->
            when (tab) {
                AppTab.VECTOR_SEARCH -> {
                    VectorSearchScreen(
                        uiState = uiState,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = viewModel::performVectorSearch,
                        onTopKChange = viewModel::updateSearchTopK,
                        onThresholdChange = viewModel::updateSearchThreshold,
                        onSelectChunk = viewModel::setSelectedResultChunk
                    )
                }

                AppTab.ASSISTANT_CHAT -> {
                    ChatAssistantScreen(
                        uiState = uiState,
                        messages = chatMessages,
                        onInputChange = viewModel::updateChatInput,
                        onSendMessage = viewModel::sendChatMessage,
                        onClearChat = viewModel::clearChat,
                        onSelectCitation = viewModel::setActiveCitation,
                        parseCitations = viewModel::parseCitations
                    )
                }

                AppTab.DOCUMENT_LIBRARY -> {
                    DocumentLibraryScreen(
                        uiState = uiState,
                        documents = documents,
                        onOpenIngestDialog = { viewModel.setIngestDialogOpen(true) },
                        onCloseIngestDialog = { viewModel.setIngestDialogOpen(false) },
                        onIngestDocument = viewModel::ingestDocument,
                        onViewDocument = viewModel::viewDocumentDetails,
                        onCloseDocumentPreview = viewModel::closeDocumentPreview,
                        onDeleteDocument = viewModel::deleteDocument,
                        onResetSampleData = viewModel::resetToSampleData
                    )
                }

                AppTab.SERVER_DIAGNOSTICS -> {
                    ServerDiagnosticsScreen(
                        uiState = uiState,
                        onUrlChange = viewModel::updateServerUrl,
                        onTestConnection = viewModel::checkServerHealth,
                        onToggleServerMode = viewModel::toggleServerMode
                    )
                }
            }
        }
    }
}
