package com.prathanbomb.diffsense.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class ApiKeyManager {

    // In-memory cache to avoid EDT calls to PasswordSafe
    private val apiKeyCache = ConcurrentHashMap<ProviderType, String?>()
    private val cacheLoaded = ConcurrentHashMap<ProviderType, Boolean>()

    private fun createCredentialAttributes(provider: ProviderType): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SUBSYSTEM, provider.name)
        )
    }

    /**
     * Retrieves the API key for the specified provider from secure storage.
     * WARNING: This method may perform slow I/O operations.
     * Use getCachedApiKey() for EDT-safe access.
     * @param provider The provider type to get the API key for
     * @return The API key or null if not set
     */
    fun getApiKey(provider: ProviderType): String? {
        val attributes = createCredentialAttributes(provider)
        val key = PasswordSafe.instance.getPassword(attributes)
        // Update cache when we fetch
        apiKeyCache[provider] = key
        cacheLoaded[provider] = true
        return key
    }

    /**
     * Gets the cached API key without accessing PasswordSafe.
     * Safe to call from EDT.
     * @param provider The provider type to get the cached API key for
     * @return The cached API key, or null if not loaded or not set
     */
    fun getCachedApiKey(provider: ProviderType): String? {
        return apiKeyCache[provider]
    }

    /**
     * Checks if the API key for a provider has been loaded into cache.
     * @param provider The provider type to check
     * @return true if the key has been loaded (may still be null if not set)
     */
    fun isCacheLoaded(provider: ProviderType): Boolean {
        return cacheLoaded[provider] == true
    }

    /**
     * Preloads API keys for all providers into cache.
     * Call this on a background thread during initialization.
     */
    fun preloadAllApiKeys() {
        ProviderType.entries.forEach { provider ->
            if (!cacheLoaded.containsKey(provider)) {
                getApiKey(provider) // This loads into cache
            }
        }
    }

    /**
     * Preloads the API key for a specific provider into cache asynchronously.
     * Safe to call from EDT.
     * @param provider The provider type to preload
     * @param callback Optional callback to run on EDT after loading
     */
    fun preloadApiKeyAsync(provider: ProviderType, callback: ((String?) -> Unit)? = null) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val key = getApiKey(provider)
            if (callback != null) {
                ApplicationManager.getApplication().invokeLater({
                    callback(key)
                }, ModalityState.any())
            }
        }
    }

    /**
     * Stores the API key for the specified provider in secure storage.
     * Also updates the in-memory cache.
     * @param provider The provider type to store the API key for
     * @param apiKey The API key to store, or null to remove
     */
    fun setApiKey(provider: ProviderType, apiKey: String?) {
        val attributes = createCredentialAttributes(provider)
        if (apiKey.isNullOrBlank()) {
            PasswordSafe.instance.set(attributes, null)
            apiKeyCache[provider] = null
        } else {
            PasswordSafe.instance.set(attributes, Credentials(provider.name, apiKey))
            apiKeyCache[provider] = apiKey
        }
        cacheLoaded[provider] = true
    }

    /**
     * Updates only the in-memory cache without persisting to PasswordSafe.
     * Useful for tracking UI changes before apply() is called.
     * @param provider The provider type
     * @param apiKey The API key value
     */
    fun setCachedApiKey(provider: ProviderType, apiKey: String?) {
        apiKeyCache[provider] = apiKey
        cacheLoaded[provider] = true
    }

    /**
     * Removes the API key for the specified provider from secure storage.
     * @param provider The provider type to remove the API key for
     */
    fun removeApiKey(provider: ProviderType) {
        setApiKey(provider, null)
    }

    /**
     * Checks if an API key is stored for the specified provider.
     * Uses cache if available, otherwise checks PasswordSafe.
     * @param provider The provider type to check
     * @return true if an API key is stored
     */
    fun hasApiKey(provider: ProviderType): Boolean {
        return if (cacheLoaded[provider] == true) {
            !apiKeyCache[provider].isNullOrBlank()
        } else {
            !getApiKey(provider).isNullOrBlank()
        }
    }

    /**
     * Gets the API key for the currently selected provider in settings.
     * Uses cache if available.
     * @return The API key or null if not set
     */
    fun getCurrentApiKey(): String? {
        val settings = PluginSettingsState.getInstance()
        val provider = settings.providerType
        return if (cacheLoaded[provider] == true) {
            apiKeyCache[provider]
        } else {
            getApiKey(provider)
        }
    }

    /**
     * Sets the API key for the currently selected provider in settings.
     * @param apiKey The API key to store
     */
    fun setCurrentApiKey(apiKey: String?) {
        val settings = PluginSettingsState.getInstance()
        setApiKey(settings.providerType, apiKey)
    }

    companion object {
        private const val SUBSYSTEM = "AICommitAssistant"

        @JvmStatic
        fun getInstance(): ApiKeyManager {
            return ApplicationManager.getApplication().getService(ApiKeyManager::class.java)
        }
    }
}
