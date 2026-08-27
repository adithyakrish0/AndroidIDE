package com.example.foldermind

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val agentLogger = remember { AgentLogger(context) }
    var logsText by remember { mutableStateOf("Loading logs...") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        logsText = agentLogger.getLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Logs") },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    Button(onClick = {
                        coroutineScope.launch {
                            agentLogger.clearLogs()
                            logsText = agentLogger.getLogs()
                        }
                    }) {
                        Text("Clear")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = logsText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}