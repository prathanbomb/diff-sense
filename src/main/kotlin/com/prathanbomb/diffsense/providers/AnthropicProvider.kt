package com.prathanbomb.diffsense.providers

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.prathanbomb.diffsense.model.AnthropicErrorResponse
import com.prathanbomb.diffsense.model.AnthropicMessage
import com.prathanbomb.diffsense.model.AnthropicRequest
import com.prathanbomb.diffsense.model.AnthropicResponse
import com.prathanbomb.diffsense.settings.ProviderType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * AI Provider implementation for Anthropic (Claude) API.
 */
class AnthropicProvider : AIProvider {

    override val providerType: ProviderType = ProviderType.ANTHROPIC

    private val providerName: String = "Anthropic"

    private val gson = Gson()

    override fun generateCommitMessage(
        prompt: String,
        apiKey: String?,
        baseUrl: String,
        model: String
    ): Result<String> {
        // Validate API key
        if (apiKey.isNullOrBlank()) {
            return Result.failure(AIProviderException.missingApiKey(providerName))
        }

        val request = buildRequest(prompt, apiKey, baseUrl, model)

        return try {
            HttpClientFactory.client.newCall(request).execute().use { response ->
                handleResponse(response)
            }
        } catch (e: IOException) {
            Result.failure(AIProviderException.networkError(e))
        }
    }

    private fun buildRequest(
        prompt: String,
        apiKey: String,
        baseUrl: String,
        model: String
    ): Request {
        val anthropicRequest = AnthropicRequest(
            model = model,
            messages = listOf(AnthropicMessage.user(prompt)),
            maxTokens = 1024
        )

        val jsonBody = gson.toJson(anthropicRequest)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val url = "${baseUrl.trimEnd('/')}/messages"

        return Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .build()
    }

    private fun handleResponse(response: okhttp3.Response): Result<String> {
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            return handleErrorResponse(response.code, responseBody)
        }

        if (responseBody.isNullOrBlank()) {
            return Result.failure(AIProviderException.parseError(providerName))
        }

        return try {
            val anthropicResponse = gson.fromJson(responseBody, AnthropicResponse::class.java)

            // Extract text from content blocks
            val message = anthropicResponse.content
                .filter { it.type == "text" }
                .mapNotNull { it.text }
                .joinToString("")

            if (message.isBlank()) {
                Result.failure(AIProviderException.parseError(providerName))
            } else {
                Result.success(message.trim())
            }
        } catch (e: JsonSyntaxException) {
            Result.failure(AIProviderException.parseError(providerName))
        }
    }

    private fun handleErrorResponse(statusCode: Int, responseBody: String?): Result<String> {
        val errorMessage = try {
            responseBody?.let {
                gson.fromJson(it, AnthropicErrorResponse::class.java)?.error?.message
            }
        } catch (e: JsonSyntaxException) {
            null
        }

        val exception = when (statusCode) {
            401 -> AIProviderException.unauthorized(providerName)
            429 -> AIProviderException.rateLimited(providerName)
            in 500..599 -> AIProviderException.serverError(providerName, statusCode)
            else -> AIProviderException(
                errorMessage ?: "$providerName error (HTTP $statusCode)",
                statusCode = statusCode
            )
        }

        return Result.failure(exception)
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
