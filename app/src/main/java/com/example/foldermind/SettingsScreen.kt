package com.example.foldermind

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRebindFolder: () -> Unit,
    onViewLogs: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    var geminiKey by remember { mutableStateOf(settingsManager.getGeminiKey() ?: "") }
    var groqKey by remember { mutableStateOf(settingsManager.getGroqKey() ?: "") }
    var isAutonomousMode by remember { mutableStateOf(settingsManager.isAutonomousMode()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    Button(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "API Keys",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = geminiKey,
                onValueChange = {
                    geminiKey = it
                    settingsManager.saveGeminiKey(it)
                },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = groqKey,
                onValueChange = {
                    groqKey = it
                    settingsManager.saveGroqKey(it)
                },
                label = { Text("Groq API Key (Fallback)") },
                modifier = Modifier.fillMaxWidth()
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Agent Behavior",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Autonomous Mode")
                    Text(
                        text = "If off, asks for confirmation before tools execute.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAutonomousMode,
                    onCheckedChange = {
                        isAutonomousMode = it
                        settingsManager.setAutonomousMode(it)
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Storage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onRebindFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rebind Folder")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Debugging",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = onViewLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("View Logs")
            }
        }
    }
}
