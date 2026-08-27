package com.example.foldermind

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.FunctionCallingConfig
import com.google.ai.client.generativeai.type.FunctionResponsePart
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.Schema
import com.google.ai.client.generativeai.type.Tool
import com.google.ai.client.generativeai.type.ToolConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.defineFunction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.ai.client.generativeai.type.FunctionType

class FolderAgent(
    private val context: Context,
    private val boundFolderUri: Uri,
    apiKey: String
) {
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

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        systemInstruction = content {
            text("You are an AI assistant that manages files in a specific folder on the user's device. " +
                    "Your primary way of helping the user is by creating and modifying markdown files in this folder. " +
                    "When asked to note something down, create a list, or similar tasks, use the `write_file` tool. " +
                    "Your chat replies must be terse — a one-line confirmation of what you did (e.g., 'Added 3 items to shopping-list.md'), never a conversational essay. " +
                    "The content belongs in the file, not the chat bubble.")
        },
        tools = listOf(Tool(listOf(writeFileDeclaration))),
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

    suspend fun sendMessage(chatHistory: List<Content>, message: String): String {
        val chat = model.startChat(chatHistory)

        var response = chat.sendMessage(message)

        var toolCallCount = 0
        while (response.functionCalls.isNotEmpty() && toolCallCount < 5) {
            toolCallCount++
            val functionCall = response.functionCalls.first()

            if (functionCall.name == "write_file") {
                val args = functionCall.args
                val path = args["path"] as? String
                val contentStr = args["content"] as? String

                val result = if (path != null && contentStr != null) {
                    executeWriteFile(path, contentStr)
                } else {
                    JSONObject().put("error", "Missing arguments").put("success", false)
                }

                response = chat.sendMessage(
                    content {
                        part(FunctionResponsePart(functionCall.name, result))
                    }
                )
            } else {
                 response = chat.sendMessage(
                    content {
                        part(FunctionResponsePart(functionCall.name, JSONObject().put("error", "Unknown function").put("success", false)))
                    }
                )
            }
        }

        return response.text ?: "No response from agent."
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