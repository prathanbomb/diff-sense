package com.prathanbomb.diffsense.providers

import com.prathanbomb.diffsense.settings.ProviderType

/**
 * Interface for AI providers that generate commit messages.
 */
interface AIProvider {

    /**
     * The type of this provider.
     */
    val providerType: ProviderType

    /**
     * Generates a commit message based on the provided prompt.
     *
     * @param prompt The full prompt including the diff content
     * @param apiKey The API key for authentication (may be null for local providers)
     * @param baseUrl The base URL for the API endpoint
     * @param model The model identifier to use
     * @return Result containing the generated message or an error
     */
    fun generateCommitMessage(
        prompt: String,
        apiKey: String?,
        baseUrl: String,
        model: String
    ): Result<String>

    companion object {
        /**
         * Creates an AIProvider instance for the specified provider type.
         */
        fun create(providerType: ProviderType): AIProvider {
            return when (providerType) {
                ProviderType.OPENAI -> OpenAIProvider()
                ProviderType.ANTHROPIC -> AnthropicProvider()
                ProviderType.OLLAMA -> OllamaProvider()
                ProviderType.CUSTOM -> OpenAICompatibleProvider()
            }
        }
    }
}

/**
 * Exception thrown when an AI provider encounters an error.
 */
class AIProviderException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        fun unauthorized(provider: String): AIProviderException {
            return AIProviderException(
                "Invalid API key for $provider. Please check your settings.",
                statusCode = 401
            )
        }

        fun rateLimited(provider: String): AIProviderException {
            return AIProviderException(
                "Rate limited by $provider. Please wait and try again.",
                statusCode = 429
            )
        }

        fun serverError(provider: String, statusCode: Int): AIProviderException {
            return AIProviderException(
                "$provider server error (HTTP $statusCode). Please try again later.",
                statusCode = statusCode
            )
        }

        fun networkError(cause: Throwable): AIProviderException {
            return AIProviderException(
                "Network error: ${cause.message ?: "Connection failed"}",
                cause = cause
            )
        }

        fun parseError(provider: String): AIProviderException {
            return AIProviderException(
                "Unexpected response format from $provider."
            )
        }

        fun missingApiKey(provider: String): AIProviderException {
            return AIProviderException(
                "API key required for $provider. Please configure it in Settings."
            )
        }
    }
}
