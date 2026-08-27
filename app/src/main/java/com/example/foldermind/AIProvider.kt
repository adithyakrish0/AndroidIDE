package com.example.foldermind

import com.google.ai.client.generativeai.type.Content

sealed class ProviderResponse {
    data class Text(val text: String) : ProviderResponse()
    data class ToolCalls(val functionCalls: List<ToolCall>) : ProviderResponse()
}

data class ToolCall(val id: String, val name: String, val args: Map<String, String>)

interface AIProvider {
    suspend fun sendMessage(chatHistory: List<Content>): ProviderResponse
}
