package com.prathanbomb.diffsense.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.prathanbomb.diffsense.providers.AIProvider
import com.prathanbomb.diffsense.providers.AIProviderException
import com.prathanbomb.diffsense.settings.ApiKeyManager
import com.prathanbomb.diffsense.settings.PluginSettingsState
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class CommitMessageGenerator {

    private val settings: PluginSettingsState
        get() = PluginSettingsState.getInstance()

    private val apiKeyManager: ApiKeyManager
        get() = ApiKeyManager.getInstance()

    /**
     * Cache for generated messages.
     * Key: hash of (diff + provider + model + template)
     * Value: CachedMessage with timestamp and content
     */
    private val messageCache = ConcurrentHashMap<String, CachedMessage>()

    /**
     * Cache TTL in milliseconds (5 minutes)
     */
    private val cacheTtlMs = 5 * 60 * 1000L

    /**
     * Maximum cache size
     */
    private val maxCacheSize = 20

    /**
     * Generates a commit message for the provided diff content.
     * Uses caching to avoid redundant API calls for the same diff.
     *
     * @param diff The diff content to generate a message for
     * @return Result containing the generated message or an error
     */
    fun generate(diff: String): Result<String> {
        // Validate diff content
        val validationResult = validateDiff(diff)
        if (validationResult.isFailure) {
            return validationResult
        }

        // Validate API key if required
        val apiKeyResult = validateApiKey()
        if (apiKeyResult.isFailure) {
            return apiKeyResult
        }

        // Check cache first
        val cacheKey = computeCacheKey(diff)
        val cachedMessage = messageCache[cacheKey]
        if (cachedMessage != null && !cachedMessage.isExpired(cacheTtlMs)) {
            return Result.success(cachedMessage.message)
        }

        // Build the prompt
        val prompt = buildPrompt(settings.promptTemplate, diff)

        // Get the appropriate provider
        val provider = AIProvider.create(settings.providerType)

        // Make the API call
        val result = provider.generateCommitMessage(
            prompt = prompt,
            apiKey = apiKeyManager.getApiKey(settings.providerType),
            baseUrl = settings.effectiveBaseUrl,
            model = settings.effectiveModelName
        )

        // Cache successful results
        if (result.isSuccess) {
            result.getOrNull()?.let { message ->
                cleanupCacheIfNeeded()
                messageCache[cacheKey] = CachedMessage(message)
            }
        }

        return result
    }

    /**
     * Computes a cache key based on diff, provider, model, and template.
     */
    private fun computeCacheKey(diff: String): String {
        val input = "${diff}|${settings.providerType}|${settings.effectiveModelName}|${settings.promptTemplate}"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Removes expired entries and limits cache size.
     */
    private fun cleanupCacheIfNeeded() {
        // Remove expired entries
        val now = System.currentTimeMillis()
        messageCache.entries.removeIf { it.value.isExpired(cacheTtlMs) }

        // Limit cache size by removing oldest entries
        if (messageCache.size >= maxCacheSize) {
            val oldest = messageCache.entries
                .sortedBy { it.value.timestamp }
                .take(messageCache.size - maxCacheSize + 1)
            oldest.forEach { messageCache.remove(it.key) }
        }
    }

    /**
     * Clears the message cache.
     */
    fun clearCache() {
        messageCache.clear()
    }

    /**
     * Validates the diff content before generation.
     *
     * @param diff The diff to validate
     * @return Success if valid, Failure with appropriate error otherwise
     */
    private fun validateDiff(diff: String): Result<String> {
        if (diff.isBlank()) {
            return Result.failure(
                GenerationException.noChanges()
            )
        }
        return Result.success(diff)
    }

    /**
     * Validates that the API key is available if required.
     *
     * @return Success if valid, Failure with appropriate error otherwise
     */
    private fun validateApiKey(): Result<String> {
        if (settings.requiresApiKey) {
            val apiKey = apiKeyManager.getApiKey(settings.providerType)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(
                    AIProviderException.missingApiKey(settings.providerType.displayName)
                )
            }
        }
        return Result.success("")
    }

    /**
     * Builds the full prompt by injecting the diff into the template.
     *
     * @param template The prompt template with {diff} placeholder
     * @param diff The diff content to inject
     * @return The complete prompt ready for the AI provider
     */
    fun buildPrompt(template: String, diff: String): String {
        val effectiveTemplate = template.ifBlank { PluginSettingsState.DEFAULT_PROMPT_TEMPLATE }
        return effectiveTemplate.replace("{diff}", diff)
    }

    companion object {
        @JvmStatic
        fun getInstance(): CommitMessageGenerator {
            return ApplicationManager.getApplication().getService(CommitMessageGenerator::class.java)
        }
    }
}

/**
 * Cached commit message with timestamp.
 */
private data class CachedMessage(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun isExpired(ttlMs: Long): Boolean {
        return System.currentTimeMillis() - timestamp > ttlMs
    }
}

/**
 * Exception for commit message generation errors.
 */
class GenerationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    companion object {
        fun noChanges(): GenerationException {
            return GenerationException("No changes selected. Please select files to include in the commit.")
        }

        fun generationFailed(cause: Throwable): GenerationException {
            return GenerationException(
                "Failed to generate commit message: ${cause.message ?: "Unknown error"}",
                cause
            )
        }
    }
}
