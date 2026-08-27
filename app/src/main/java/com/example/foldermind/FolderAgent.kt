package com.example.foldermind

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionCallingConfig
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.ToolConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import com.google.ai.client.generativeai.type.FunctionType
import com.google.ai.client.generativeai.type.ResponseStoppedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.UnknownHostException
import java.net.ConnectException

data class ToolConfirmationRequest(
    val toolName: String,
    val args: Map<String, String>,
    val originalContent: String? = null
)

class FolderAgent(
    private val context: Context,
    private val boundFolderUri: Uri,
    geminiApiKey: String,
    groqApiKey: String?,
    private val confirmHandler: (suspend (ToolConfirmationRequest) -> Boolean)? = null
) {
    private val listDirDeclaration = defineFunction(
        name = "list_dir",
        description = "Lists files and directories in the bound folder.",
        Schema(
            name = "path",
            description = "Optional sub-path relative to the root bound folder.",
            type = FunctionType.STRING
        )
    ) { path ->
        executeListDir(path as? String)
    }

    private val readFileDeclaration = defineFunction(
        name = "read_file",
        description = "Reads the text content of a file in the bound folder.",
        Schema(
            name = "path",
            description = "The path (filename) of the file to read, e.g. shopping-list.md",
            type = FunctionType.STRING
        )
    ) { path ->
        executeReadFile(path as String)
    }

    private val createFileDeclaration = defineFunction(
        name = "create_file",
        description = "Creates a new file in the bound folder. Fails if the file already exists.",
        Schema(
            name = "path",
            description = "The path (filename) of the file to create, e.g. notes.md",
            type = FunctionType.STRING
        ),
        Schema(
            name = "content",
            description = "The full content to write to the new file.",
            type = FunctionType.STRING
        )
    ) { path, content ->
        executeCreateFile(path as String, content as String)
    }

    private val writeFileDeclaration = defineFunction(
        name = "write_file",
        description = "Creates a new file or overwrites an existing file in the bound folder.",
        Schema(
            name = "path",
            description = "The path (filename) of the file to create or overwrite, e.g. shopping-list.md",
            type = FunctionType.STRING
        ),
        Schema(
            name = "content",
            description = "The full content to write to the file.",
            type = FunctionType.STRING
        )
    ) { path, content ->
        executeWriteFile(path as String, content as String)
    }

    private val deleteFileDeclaration = defineFunction(
        name = "delete_file",
        description = "Deletes a file from the bound folder.",
        Schema(
            name = "path",
            description = "The path (filename) of the file to delete.",
            type = FunctionType.STRING
        )
    ) { path ->
        executeDeleteFile(path as String)
    }

    private val renameFileDeclaration = defineFunction(
        name = "rename_file",
        description = "Renames a file in the bound folder.",
        Schema(
            name = "old_path",
            description = "The current path (filename) of the file.",
            type = FunctionType.STRING
        ),
        Schema(
            name = "new_path",
            description = "The new path (filename) for the file.",
            type = FunctionType.STRING
        )
    ) { oldPath, newPath ->
        executeRenameFile(oldPath as String, newPath as String)
    }

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = geminiApiKey,
        systemInstruction = content {
            text("You are an AI assistant that manages files in a specific folder on the user's device. " +
                    "Your primary way of helping the user is by creating and modifying markdown files in this folder. " +
                    "When asked to note something down, create a list, or similar tasks, use the provided tools.\n" +
                    "IMPORTANT RULES:\n" +
                    "1. Context strategy: Do NOT ask for a full folder listing on every message. Call `list_dir` on demand when you need to know what's in the folder.\n" +
                    "2. File-matching rule: Before creating a new file, always `list_dir` first and check whether an existing file already matches the intent of the request. Only create a new file when nothing existing fits.\n" +
                    "3. Freshness rule: Before editing any existing file, you must re-read its current on-disk content first using `read_file` — never assume the in-memory/chat-history version is still accurate.\n" +
                    "Your chat replies must be terse — a one-line confirmation of what you did (e.g., 'Added 3 items to shopping-list.md'), never a conversational essay. " +
                    "The content belongs in the file, not the chat bubble.")
        },
        tools = listOf(Tool(listOf(
            listDirDeclaration,
            readFileDeclaration,
            createFileDeclaration,
            writeFileDeclaration,
            deleteFileDeclaration,
            renameFileDeclaration
        ))),
        toolConfig = ToolConfig(
            functionCallingConfig = FunctionCallingConfig(mode = FunctionCallingConfig.Mode.AUTO)
        ),
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
        )
    )

    private val geminiProvider = GeminiProvider(model)
    private val groqProvider = groqApiKey?.takeIf { it.isNotBlank() }?.let { GroqProvider(it) }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun sendMessage(chatHistory: List<Content>, message: String): String {
        if (!isInternetAvailable()) {
            return "No internet connection before attempting a request."
        }

        val currentHistory = chatHistory.toMutableList()
        currentHistory.add(content("user") { text(message) })

        var providerNotice = ""
        var currentProvider: AIProvider = geminiProvider

        var response = try {
            currentProvider.sendMessage(currentHistory)
        } catch (e: Exception) {
            val isQuotaError = e.message?.contains("429") == true || e.message?.contains("quota") == true || e is ResponseStoppedException
            if (isQuotaError) {
                if (groqProvider != null) {
                    currentProvider = groqProvider
                    providerNotice = "\n\n(Switched to backup model due to quota limit)"
                    try {
                        currentProvider.sendMessage(currentHistory)
                    } catch (e2: Exception) {
                        if (e2.message?.contains("429") == true || e2.message?.contains("quota") == true) {
                            return "Daily limit reached on both providers — try again later"
                        }
                        throw e2
                    }
                } else {
                    return "Gemini quota exceeded and no Groq fallback key configured."
                }
            } else {
                throw e
            }
        }

        var toolCallCount = 0
        while (response is ProviderResponse.ToolCalls && toolCallCount < 15) {
            toolCallCount++
            val toolCalls = (response as ProviderResponse.ToolCalls).functionCalls
            val firstCall = toolCalls.first()

            val callContent = content("model") {
                part(FunctionCallPart(firstCall.name, firstCall.args))
            }
            currentHistory.add(callContent)

            val args = firstCall.args

            val isAutonomous = SettingsManager(context).isAutonomousMode()
            var isApproved = true
            if (!isAutonomous && confirmHandler != null) {
                var originalContent: String? = null
                if (firstCall.name == "write_file") {
                    val path = args["path"]
                    if (path != null) {
                        withContext(Dispatchers.IO) {
                            var currentFolder = DocumentFile.fromTreeUri(context, boundFolderUri)
                            var targetFile: DocumentFile? = null
                            if (currentFolder != null) {
                                val parts = path.split("/")
                                for (i in 0 until parts.size - 1) {
                                    val part = parts[i]
                                    if (part.isNotEmpty()) {
                                        currentFolder = currentFolder?.findFile(part)
                                    }
                                }
                                targetFile = currentFolder?.findFile(parts.last())

                                if (targetFile != null && !targetFile.isDirectory) {
                                    try {
                                        context.contentResolver.openInputStream(targetFile.uri)?.use { inputStream ->
                                            originalContent = inputStream.bufferedReader().use { it.readText() }
                                        }
                                    } catch (e: Exception) {
                                        // Ignore error fetching original content
                                    }
                                }
                            }
                        }
                    }
                }

                val request = ToolConfirmationRequest(firstCall.name, args, originalContent)
                isApproved = confirmHandler.invoke(request)
            }

            val result = if (!isApproved) {
                JSONObject().put("error", "User rejected tool execution").put("success", false)
            } else when (firstCall.name) {
                "list_dir" -> {
                    val path = args["path"]
                    executeListDir(path)
                }
                "read_file" -> {
                    val path = args["path"]
                    if (path != null) executeReadFile(path) else JSONObject().put("error", "Missing path").put("success", false)
                }
                "create_file" -> {
                    val path = args["path"]
                    val contentStr = args["content"]
                    if (path != null && contentStr != null) executeCreateFile(path, contentStr) else JSONObject().put("error", "Missing args").put("success", false)
                }
                "write_file" -> {
                    val path = args["path"]
                    val contentStr = args["content"]
                    if (path != null && contentStr != null) executeWriteFile(path, contentStr) else JSONObject().put("error", "Missing args").put("success", false)
                }
                "delete_file" -> {
                    val path = args["path"]
                    if (path != null) executeDeleteFile(path) else JSONObject().put("error", "Missing path").put("success", false)
                }
                "rename_file" -> {
                    val oldPath = args["old_path"]
                    val newPath = args["new_path"]
                    if (oldPath != null && newPath != null) executeRenameFile(oldPath, newPath) else JSONObject().put("error", "Missing args").put("success", false)
                }
                else -> JSONObject().put("error", "Unknown function").put("success", false)
            }

            val responseContent = content("function") {
                part(FunctionResponsePart(firstCall.name, result))
            }
            currentHistory.add(responseContent)

            response = try {
                currentProvider.sendMessage(currentHistory)
            } catch (e: Exception) {
                 val isQuotaError = e.message?.contains("429") == true || e.message?.contains("quota") == true
                 if (isQuotaError) {
                     if (currentProvider == geminiProvider && groqProvider != null) {
                         currentProvider = groqProvider
                         providerNotice = "\n\n(Switched to backup model due to quota limit)"
                         try {
                             currentProvider.sendMessage(currentHistory)
                         } catch (e2: Exception) {
                            if (e2.message?.contains("429") == true || e2.message?.contains("quota") == true) {
                                return "Daily limit reached on both providers — try again later"
                            }
                            throw e2
                         }
                     } else {
                         return "Daily limit reached on both providers — try again later"
                     }
                 } else {
                     throw e
                 }
            }
        }

        if (toolCallCount >= 15) {
            return "Agent loop cap reached (~15 tool calls). Stopped to prevent runaway execution."
        }

        return if (response is ProviderResponse.Text) {
            response.text + providerNotice
        } else {
            "No response from agent." + providerNotice
        }
    }

    private suspend fun executeListDir(path: String?): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                var folder = DocumentFile.fromTreeUri(context, boundFolderUri)
                    ?: return@withContext JSONObject().put("error", "Could not access bound folder").put("success", false)

                if (path != null && path.isNotEmpty()) {
                    val subFolder = folder.findFile(path)
                    if (subFolder == null || !subFolder.isDirectory) {
                        return@withContext JSONObject().put("error", "Directory not found: $path").put("success", false)
                    }
                    folder = subFolder
                }

                val files = folder.listFiles().map {
                    JSONObject()
                        .put("name", it.name)
                        .put("isDirectory", it.isDirectory)
                }

                JSONObject().put("success", true).put("files", org.json.JSONArray(files))
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Unknown error").put("success", false)
            }
        }
    }

    private suspend fun executeReadFile(path: String): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, boundFolderUri)
                    ?: return@withContext JSONObject().put("error", "Could not access bound folder").put("success", false)

                val file = folder.findFile(path)
                if (file == null || file.isDirectory) {
                    return@withContext JSONObject().put("error", "File not found or is a directory: $path").put("success", false)
                }

                val content = context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: return@withContext JSONObject().put("error", "Failed to read file").put("success", false)

                JSONObject().put("success", true).put("content", content)
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Unknown error").put("success", false)
            }
        }
    }

    private suspend fun executeCreateFile(path: String, contentStr: String): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, boundFolderUri)
                    ?: return@withContext JSONObject().put("error", "Could not access bound folder").put("success", false)

                var file = folder.findFile(path)
                if (file != null) {
                    return@withContext JSONObject().put("error", "File already exists: $path").put("success", false)
                }

                val mimeType = if (path.endsWith(".md")) "text/markdown" else "text/plain"
                file = folder.createFile(mimeType, path)

                if (file == null) {
                     return@withContext JSONObject().put("error", "Failed to create file").put("success", false)
                }

                context.contentResolver.openOutputStream(file.uri, "w")?.use { outputStream ->
                    outputStream.write(contentStr.toByteArray())
                } ?: return@withContext JSONObject().put("error", "Failed to open file for writing").put("success", false)

                JSONObject().put("success", true).put("message", "File created successfully")
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Unknown error").put("success", false)
            }
        }
    }

    private suspend fun executeDeleteFile(path: String): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, boundFolderUri)
                    ?: return@withContext JSONObject().put("error", "Could not access bound folder").put("success", false)

                val file = folder.findFile(path)
                if (file == null) {
                    return@withContext JSONObject().put("error", "File not found: $path").put("success", false)
                }

                if (file.delete()) {
                    JSONObject().put("success", true).put("message", "File deleted successfully")
                } else {
                    JSONObject().put("error", "Failed to delete file").put("success", false)
                }
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Unknown error").put("success", false)
            }
        }
    }

    private suspend fun executeRenameFile(oldPath: String, newPath: String): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, boundFolderUri)
                    ?: return@withContext JSONObject().put("error", "Could not access bound folder").put("success", false)

                val file = folder.findFile(oldPath)
                if (file == null) {
                    return@withContext JSONObject().put("error", "File not found: $oldPath").put("success", false)
                }

                if (file.renameTo(newPath)) {
                    JSONObject().put("success", true).put("message", "File renamed successfully")
                } else {
                    JSONObject().put("error", "Failed to rename file").put("success", false)
                }
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Unknown error").put("success", false)
            }
        }
    }

    private suspend fun executeWriteFile(path: String, contentStr: String): JSONObject {
        return withContext(Dispatchers.IO) {
            try {
                val folder = DocumentFile.fromTreeUri(context, boundFolderUri)
                    ?: return@withContext JSONObject().put("error", "Could not access bound folder").put("success", false)

                var file = folder.findFile(path)
                if (file == null) {
                    val mimeType = if (path.endsWith(".md")) "text/markdown" else "text/plain"
                    file = folder.createFile(mimeType, path)
                }

                if (file == null) {
                     return@withContext JSONObject().put("error", "Failed to create file").put("success", false)
                }

                context.contentResolver.openOutputStream(file.uri, "w")?.use { outputStream ->
                    outputStream.write(contentStr.toByteArray())
                } ?: return@withContext JSONObject().put("error", "Failed to open file for writing").put("success", false)

                JSONObject().put("success", true).put("message", "File written successfully")
            } catch (e: Exception) {
                JSONObject().put("error", e.message ?: "Unknown error").put("success", false)
            }
        }
    }
}
