package com.example.foldermind

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(boundFolderUri: Uri, onSettingsClick: () -> Unit, onChatClick: () -> Unit) {
    val context = LocalContext.current
    var files by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    LaunchedEffect(boundFolderUri) {
        val folder = DocumentFile.fromTreeUri(context, boundFolderUri)
        if (folder != null && folder.isDirectory) {
            files = folder.listFiles().toList()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onChatClick) {
                Icon(Icons.Filled.Send, contentDescription = "Chat")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onSettingsClick) {
                    Text("Settings")
                }
            }

            if (files.isEmpty()) {
                Text(
                    text = "No files found in the bound folder.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn {
                    items(files) { file ->
                        FileListItem(file = file)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun FileListItem(file: DocumentFile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = file.name ?: "Unknown",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (file.isDirectory) FontWeight.Bold else FontWeight.Normal
        )
        if (file.isDirectory) {
             Text(
                text = "Directory",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
