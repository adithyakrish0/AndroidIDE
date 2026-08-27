package com.example.foldermind

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import java.util.UUID

class GeminiProvider(private val model: GenerativeModel) : AIProvider {

    override suspend fun sendMessage(chatHistory: List<Content>): ProviderResponse {
        val response = model.generateContent(*chatHistory.toTypedArray())

        return if (response.functionCalls.isNotEmpty()) {
            val calls = response.functionCalls.map { functionCall ->
                ToolCall(
                    id = UUID.randomUUID().toString(),
                    name = functionCall.name,
                    args = functionCall.args.entries.associate { (k, v) -> k to v.toString() }
                )
            }
            ProviderResponse.ToolCalls(calls)
        } else {
            ProviderResponse.Text(response.text ?: "")
        }
    }
}
