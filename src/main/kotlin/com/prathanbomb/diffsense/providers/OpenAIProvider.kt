package com.prathanbomb.diffsense.providers

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.prathanbomb.diffsense.model.ChatMessage
import com.prathanbomb.diffsense.model.OpenAIChatRequest
import com.prathanbomb.diffsense.model.OpenAIChatResponse
import com.prathanbomb.diffsense.model.OpenAIErrorResponse
import com.prathanbomb.diffsense.settings.ProviderType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI Provider implementation for OpenAI API.
 * Also serves as base for OpenAI-compatible APIs (Ollama, custom endpoints).
 */
open class OpenAIProvider : AIProvider {

    override val providerType: ProviderType = ProviderType.OPENAI

    protected open val providerName: String = "OpenAI"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // 2 minutes for slow LLM APIs
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    override fun generateCommitMessage(
        prompt: String,
        apiKey: String?,
        baseUrl: String,
        model: String
    ): Result<String> {
        // Validate API key if required
        if (providerType.requiresApiKey && apiKey.isNullOrBlank()) {
            return Result.failure(AIProviderException.missingApiKey(providerName))
        }

        val request = buildRequest(prompt, apiKey, baseUrl, model)

        return try {
            client.newCall(request).execute().use { response ->
                handleResponse(response)
            }
        } catch (e: IOException) {
            Result.failure(AIProviderException.networkError(e))
        }
    }

    protected open fun buildRequest(
        prompt: String,
        apiKey: String?,
        baseUrl: String,
        model: String
    ): Request {
        val chatRequest = OpenAIChatRequest(
            model = model,
            messages = listOf(ChatMessage.user(prompt))
        )

        val jsonBody = gson.toJson(chatRequest)
        val requestBody = jsonBody.toRequestBody(JSON_MEDIA_TYPE)

        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        return Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                if (!apiKey.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
                addHeader("Content-Type", "application/json")
            }
            .build()
    }

    protected open fun handleResponse(response: okhttp3.Response): Result<String> {
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            return handleErrorResponse(response.code, responseBody)
        }

        if (responseBody.isNullOrBlank()) {
            return Result.failure(AIProviderException.parseError(providerName))
        }

        return try {
            val chatResponse = gson.fromJson(responseBody, OpenAIChatResponse::class.java)
            val message = chatResponse.choices.firstOrNull()?.message?.content

            if (message.isNullOrBlank()) {
                Result.failure(AIProviderException.parseError(providerName))
            } else {
                Result.success(message.trim())
            }
        } catch (e: JsonSyntaxException) {
            Result.failure(AIProviderException.parseError(providerName))
        }
    }

    protected open fun handleErrorResponse(statusCode: Int, responseBody: String?): Result<String> {
        val errorMessage = try {
            responseBody?.let {
                gson.fromJson(it, OpenAIErrorResponse::class.java)?.error?.message
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
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
