package com.example.foldermind

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.TextPart
import kotlinx.coroutines.launch

class ChatViewModel(
    private val boundFolderUri: Uri,
    private val settingsManager: SettingsManager,
    private val chatRepository: ChatRepository,
    private val folderAgent: FolderAgent?
) : ViewModel() {

    var messages by mutableStateOf<List<Content>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = chatRepository.loadHistory(boundFolderUri)
            messages = history
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || folderAgent == null) return

        val userMessage = Content(role = "user", parts = listOf(TextPart(text)))
        val currentHistory = messages
        messages = currentHistory + userMessage
        isLoading = true

        viewModelScope.launch {
            try {
                val responseText = folderAgent.sendMessage(currentHistory, text)
                val modelMessage = Content(role = "model", parts = listOf(TextPart(responseText)))
                val newHistory = messages + modelMessage
                messages = newHistory
                chatRepository.saveHistory(boundFolderUri, newHistory)
            } catch (e: Exception) {
                val errorMessage = Content(role = "model", parts = listOf(TextPart("Error: ${e.message}")))
                messages = messages + errorMessage
            } finally {
                isLoading = false
            }
        }
    }
}

class ChatViewModelFactory(
    private val boundFolderUri: Uri,
    private val settingsManager: SettingsManager,
    private val chatRepository: ChatRepository,
    private val folderAgent: FolderAgent?
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(boundFolderUri, settingsManager, chatRepository, folderAgent) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(boundFolderUri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val apiKey = settingsManager.getGeminiKey()
    val groqKey = settingsManager.getGroqKey()

    if (apiKey.isNullOrBlank()) {
        MissingApiKeyScreen(onBack)
        return
    }

    val folderAgent = remember { FolderAgent(context, boundFolderUri, apiKey, groqKey) }
    val chatRepository = remember { ChatRepository(context) }

    val viewModel: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(boundFolderUri, settingsManager, chatRepository, folderAgent)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Chat") },
                navigationIcon = {
                    Button(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            MessageList(
                messages = viewModel.messages,
                modifier = Modifier.weight(1f)
            )

            ChatInput(
                isLoading = viewModel.isLoading,
                onSend = { viewModel.sendMessage(it) }
            )
        }
    }
}

@Composable
fun MissingApiKeyScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Missing API Key",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "Add your Gemini API key in Settings to continue.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(onClick = onBack) {
            Text("Go Back")
        }
    }
}

@Composable
fun MessageList(messages: List<Content>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(messages) { content ->
            val isUser = content.role == "user"
            val text = content.parts.filterIsInstance<TextPart>().firstOrNull()?.text ?: ""

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .widthIn(max = 280.dp)
                ) {
                    Text(
                        text = text,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInput(isLoading: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") },
            enabled = !isLoading
        )
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = !isLoading && text.isNotBlank()
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send")
        }
    }
}
