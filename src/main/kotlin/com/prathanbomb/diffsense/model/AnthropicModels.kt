package com.prathanbomb.diffsense.model

import com.google.gson.annotations.SerializedName

/**
 * Anthropic Messages API request model.
 */
data class AnthropicRequest(
    val model: String,
    val messages: List<AnthropicMessage>,
    @SerializedName("max_tokens")
    val maxTokens: Int = 1024,
    val temperature: Double = 0.7
)

/**
 * Message for Anthropic API.
 */
data class AnthropicMessage(
    val role: String,
    val content: String
) {
    companion object {
        fun user(content: String) = AnthropicMessage("user", content)
        fun assistant(content: String) = AnthropicMessage("assistant", content)
    }
}

/**
 * Anthropic Messages API response model.
 */
data class AnthropicResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<ContentBlock>,
    val model: String?,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: AnthropicUsage?
)

/**
 * Content block in Anthropic response.
 */
data class ContentBlock(
    val type: String,
    val text: String?
)

/**
 * Token usage for Anthropic API.
 */
data class AnthropicUsage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)

/**
 * Anthropic error response model.
 */
data class AnthropicErrorResponse(
    val type: String?,
    val error: AnthropicError?
)

data class AnthropicError(
    val type: String?,
    val message: String?
)
