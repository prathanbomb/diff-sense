package com.prathanbomb.diffsense.providers

import com.google.gson.Gson
import com.prathanbomb.diffsense.model.ChatMessage
import com.prathanbomb.diffsense.model.OpenAIChatRequest
import com.prathanbomb.diffsense.settings.ProviderType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI Provider implementation for Ollama (local LLM).
 * Uses OpenAI-compatible API format.
 */
class OllamaProvider : OpenAIProvider() {

    override val providerType: ProviderType = ProviderType.OLLAMA

    override val providerName: String = "Ollama"

    override fun generateCommitMessage(
        prompt: String,
        apiKey: String?,
        baseUrl: String,
        model: String
    ): Result<String> {
        // Ollama doesn't require API key, so we skip that validation
        val request = buildRequest(prompt, apiKey, baseUrl, model)

        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // Longer timeout for local models
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                handleResponse(response)
            }
        } catch (e: IOException) {
            Result.failure(AIProviderException.networkError(e))
        }
    }

    override fun buildRequest(
        prompt: String,
        apiKey: String?,
        baseUrl: String,
        model: String
    ): Request {
        val chatRequest = OpenAIChatRequest(
            model = model,
            messages = listOf(ChatMessage.user(prompt))
        )

        val jsonBody = Gson().toJson(chatRequest)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        return Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                // Only add Authorization if API key is provided (some Ollama setups may require it)
                if (!apiKey.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
                addHeader("Content-Type", "application/json")
            }
            .build()
    }
}
