package com.example.foldermind

import android.net.Uri
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(fileUri: Uri, onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fileContent by remember { mutableStateOf("") }
    var isRawText by remember { mutableStateOf(false) }

    LaunchedEffect(fileUri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    fileContent = reader.readText()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Raw")
                        Switch(
                            checked = isRawText,
                            onCheckedChange = { isRawText = it },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (isRawText) {
                Text(
                    text = fileContent,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            } else {
                val markwon = remember(context) {
                    Markwon.builder(context)
                        .usePlugin(TablePlugin.create(context))
                        .usePlugin(TaskListPlugin.create(context))
                        .build()
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        TextView(ctx).apply {
                            setupInteractiveMarkdown(this, markwon, fileContent) { index ->
                                val newContent = MarkdownUtils.toggleCheckbox(fileContent, index)
                                fileContent = newContent

                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                                            outputStream.write(newContent.toByteArray())
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                    },
                    update = { view ->
                        setupInteractiveMarkdown(view, markwon, fileContent) { index ->
                            val newContent = MarkdownUtils.toggleCheckbox(fileContent, index)
                            fileContent = newContent

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openOutputStream(fileUri, "wt")?.use { outputStream ->
                                        outputStream.write(newContent.toByteArray())
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
