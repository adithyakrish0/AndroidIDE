package com.example.foldermind

import android.content.Context
import android.net.Uri
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.TextPart
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class ChatRepository(private val context: Context) {
    private val gson = Gson()

    private fun getFileForUri(boundFolderUri: Uri): File {
        val encodedUri = URLEncoder.encode(boundFolderUri.toString(), StandardCharsets.UTF_8.toString())
        val filename = "chat_history_$encodedUri.json"
        return File(context.filesDir, filename)
    }

    suspend fun saveHistory(boundFolderUri: Uri, history: List<Content>) = withContext(Dispatchers.IO) {
        try {
            val file = getFileForUri(boundFolderUri)
            val jsonElements = history.map { content ->
                val role = content.role
                val parts = content.parts.mapNotNull {
                    if (it is TextPart) {
                        mapOf("text" to it.text)
                    } else {
                        // Very simple persistence for now: drop function calls/responses from history.
                        // We primarily care about user prompts and model text responses.
                        null
                    }
                }
                mapOf("role" to role, "parts" to parts)
            }

            val json = gson.toJson(jsonElements)
            FileWriter(file).use { it.write(json) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadHistory(boundFolderUri: Uri): List<Content> = withContext(Dispatchers.IO) {
        val file = getFileForUri(boundFolderUri)
        if (!file.exists()) return@withContext emptyList()

        try {
            FileReader(file).use { reader ->
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                val rawData: List<Map<String, Any>> = gson.fromJson(reader, type)

                rawData.mapNotNull { item ->
                    val role = item["role"] as? String ?: return@mapNotNull null
                    val partsRaw = item["parts"] as? List<Map<String, String>> ?: return@mapNotNull null

                    val textParts = partsRaw.mapNotNull { partMap ->
                        partMap["text"]?.let { TextPart(it) }
                    }
                    if (textParts.isEmpty()) return@mapNotNull null

                    Content(role = role, parts = textParts)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
