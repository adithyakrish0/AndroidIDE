package com.example.foldermind

import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.TextPart
import com.google.ai.client.generativeai.type.FunctionCallPart
import com.google.ai.client.generativeai.type.FunctionResponsePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class GroqProvider(private val apiKey: String) : AIProvider {

    // To match function calls and responses across requests without changing the Gemini `Content` schema,
    // we generate deterministic IDs based on the function name and its arguments.
    private fun generateDeterministicId(name: String, argsStr: String): String {
        val input = "$name|$argsStr"
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    override suspend fun sendMessage(chatHistory: List<Content>): ProviderResponse = withContext(Dispatchers.IO) {
        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        val conn = url.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val payload = JSONObject()
            payload.put("model", "llama3-8b-8192")

            val messages = JSONArray()
            val systemMsg = JSONObject()
            systemMsg.put("role", "system")
            systemMsg.put("content", "You are an AI assistant that manages files in a specific folder on the user's device. " +
                    "Your primary way of helping the user is by creating and modifying markdown files in this folder. " +
                    "When asked to note something down, create a list, or similar tasks, use the provided tools.\n" +
                    "IMPORTANT RULES:\n" +
                    "1. Context strategy: Do NOT ask for a full folder listing on every message. Call `list_dir` on demand when you need to know what's in the folder.\n" +
                    "2. File-matching rule: Before creating a new file, always `list_dir` first and check whether an existing file already matches the intent of the request. Only create a new file when nothing existing fits.\n" +
                    "3. Freshness rule: Before editing any existing file, you must re-read its current on-disk content first using `read_file` — never assume the in-memory/chat-history version is still accurate.\n" +
                    "Your chat replies must be terse — a one-line confirmation of what you did (e.g., 'Added 3 items to shopping-list.md'), never a conversational essay. " +
                    "The content belongs in the file, not the chat bubble.")
            messages.put(systemMsg)

            // To ensure tool_call_ids match, we will just keep track of the most recent tool call
            var lastDeterministicId = "dummy"

            for (content in chatHistory) {
                var role = when(content.role) {
                    "user" -> "user"
                    "model" -> "assistant"
                    "function" -> "tool"
                    else -> "user"
                }

                var textContent = ""
                var toolCallsArray: JSONArray? = null
                val messageObj = JSONObject()

                var isToolResponse = false
                var toolResponseObj = JSONObject()

                for (part in content.parts) {
                    if (part is TextPart) {
                        textContent += part.text
                    } else if (part is FunctionCallPart) {
                        if (toolCallsArray == null) toolCallsArray = JSONArray()
                        val tc = JSONObject()

                        val argsJson = JSONObject()
                        part.args.forEach { (k, v) -> argsJson.put(k, v) }

                        lastDeterministicId = generateDeterministicId(part.name, argsJson.toString())

                        tc.put("id", "call_$lastDeterministicId")
                        tc.put("type", "function")
                        val fn = JSONObject()
                        fn.put("name", part.name)
                        fn.put("arguments", argsJson.toString())
                        tc.put("function", fn)
                        toolCallsArray.put(tc)
                    } else if (part is FunctionResponsePart) {
                        isToolResponse = true
                        toolResponseObj.put("role", "tool")

                        // We use the last generated ID for the matching response part
                        // (Assuming responses always immediately follow calls)
                        toolResponseObj.put("tool_call_id", "call_$lastDeterministicId")
                        toolResponseObj.put("name", part.name)
                        toolResponseObj.put("content", part.response.toString())
                    }
                }

                if (isToolResponse) {
                    messages.put(toolResponseObj)
                } else if (textContent.isNotEmpty() || toolCallsArray != null) {
                    messageObj.put("role", role)
                    if (textContent.isNotEmpty()) messageObj.put("content", textContent)
                    if (toolCallsArray != null) messageObj.put("tool_calls", toolCallsArray)
                    messages.put(messageObj)
                }
            }

            payload.put("messages", messages)

            val toolsArray = JSONArray()
            toolsArray.put(createToolSchema("list_dir", "Lists files and directories in the bound folder.", mapOf("path" to "string")))
            toolsArray.put(createToolSchema("read_file", "Reads the text content of a file in the bound folder.", mapOf("path" to "string")))
            toolsArray.put(createToolSchema("create_file", "Creates a new file in the bound folder.", mapOf("path" to "string", "content" to "string")))
            toolsArray.put(createToolSchema("write_file", "Creates a new file or overwrites an existing file.", mapOf("path" to "string", "content" to "string")))
            toolsArray.put(createToolSchema("delete_file", "Deletes a file from the bound folder.", mapOf("path" to "string")))
            toolsArray.put(createToolSchema("rename_file", "Renames a file in the bound folder.", mapOf("old_path" to "string", "new_path" to "string")))

            payload.put("tools", toolsArray)
            payload.put("tool_choice", "auto")

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val responseCode = conn.responseCode
            if (responseCode == 429) {
                throw Exception("Groq 429 quota limit")
            } else if (responseCode !in 200..299) {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw Exception("Groq API error ($responseCode): $errorMsg")
            }

            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseBody)
            val choice = responseJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message")

            if (choice.has("tool_calls") && !choice.isNull("tool_calls")) {
                val callsArray = choice.getJSONArray("tool_calls")
                val toolCallsList = mutableListOf<ToolCall>()
                for (i in 0 until callsArray.length()) {
                    val tc = callsArray.getJSONObject(i)
                    if (tc.getString("type") == "function") {
                        val fn = tc.getJSONObject("function")
                        val id = tc.getString("id")
                        val name = fn.getString("name")
                        val argsRaw = fn.getString("arguments")
                        val argsJson = JSONObject(argsRaw)
                        val argsMap = mutableMapOf<String, String>()
                        argsJson.keys().forEach { k ->
                            argsMap[k] = argsJson.getString(k)
                        }
                        toolCallsList.add(ToolCall(id, name, argsMap))
                    }
                }
                ProviderResponse.ToolCalls(toolCallsList)
            } else {
                val text = if (choice.has("content") && !choice.isNull("content")) choice.getString("content") else ""
                ProviderResponse.Text(text)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun createToolSchema(name: String, desc: String, props: Map<String, String>): JSONObject {
        val tool = JSONObject()
        tool.put("type", "function")
        val function = JSONObject()
        function.put("name", name)
        function.put("description", desc)

        val parameters = JSONObject()
        parameters.put("type", "object")
        val properties = JSONObject()
        val required = JSONArray()

        props.forEach { (k, v) ->
            val propObj = JSONObject()
            propObj.put("type", v)
            properties.put(k, propObj)
            if (k != "path" || name != "list_dir") {
                 required.put(k)
            }
        }

        parameters.put("properties", properties)
        parameters.put("required", required)

        function.put("parameters", parameters)
        tool.put("function", function)
        return tool
    }
}
