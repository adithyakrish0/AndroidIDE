package com.example.foldermind

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private var boundFolderUri by mutableStateOf<Uri?>(null)

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveBoundFolderUri(uri)
            boundFolderUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        boundFolderUri = loadBoundFolderUri()

        setContent {
            var currentScreen by remember { mutableStateOf("dashboard") }
            var previewUri by remember { mutableStateOf<Uri?>(null) }

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentUri = boundFolderUri
                    if (currentUri == null) {
                        OnboardingScreen(onPickFolder = {
                            folderPickerLauncher.launch(null)
                        })
                    } else if (previewUri != null) {
                        FilePreviewScreen(
                            fileUri = previewUri!!,
                            onBack = { previewUri = null }
                        )
                    } else {
                        when (currentScreen) {
                            "settings" -> SettingsScreen(
                                onRebindFolder = { folderPickerLauncher.launch(null) },
                                onViewLogs = { currentScreen = "logs" },
                                onBack = { currentScreen = "dashboard" }
                            )
                            "chat" -> ChatScreen(
                                boundFolderUri = currentUri,
                                onBack = { currentScreen = "dashboard" }
                            )
                            "logs" -> LogsScreen(
                                onBack = { currentScreen = "settings" }
                            )
                            else -> DashboardScreen(
                                boundFolderUri = currentUri,
                                onSettingsClick = { currentScreen = "settings" },
                                onChatClick = { currentScreen = "chat" },
                                onFileClick = { uri -> previewUri = uri }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveBoundFolderUri(uri: Uri) {
        val sharedPrefs = getSharedPreferences("foldermind_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("bound_folder_uri", uri.toString()).apply()
    }

    private fun loadBoundFolderUri(): Uri? {
        val sharedPrefs = getSharedPreferences("foldermind_prefs", Context.MODE_PRIVATE)
        val uriString = sharedPrefs.getString("bound_folder_uri", null)
        return uriString?.let { Uri.parse(it) }
    }
}

@Composable
fun OnboardingScreen(onPickFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to FolderMind!",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "This app needs access to a specific folder on your device to store and manage your markdown files.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        Button(onClick = onPickFolder) {
            Text("Select Folder")
        }
    }
}
