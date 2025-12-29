package com.prathanbomb.diffsense.model

import com.google.gson.annotations.SerializedName

/**
 * OpenAI Chat Completion API request model.
 * Also used by Ollama and other OpenAI-compatible APIs.
 */
data class OpenAIChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null
)

/**
 * Chat message for OpenAI API.
 */
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        fun user(content: String) = ChatMessage("user", content)
        fun system(content: String) = ChatMessage("system", content)
        fun assistant(content: String) = ChatMessage("assistant", content)
    }
}

/**
 * OpenAI Chat Completion API response model.
 */
data class OpenAIChatResponse(
    val id: String?,
    val choices: List<Choice>,
    val usage: Usage?
)

/**
 * Choice in OpenAI response.
 */
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerializedName("finish_reason")
    val finishReason: String?
)

/**
 * Token usage information.
 */
data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

/**
 * OpenAI error response model.
 */
data class OpenAIErrorResponse(
    val error: OpenAIError?
)

data class OpenAIError(
    val message: String?,
    val type: String?,
    val code: String?
)
