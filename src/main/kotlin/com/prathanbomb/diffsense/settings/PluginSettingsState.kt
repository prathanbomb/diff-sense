package com.prathanbomb.diffsense.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "AICommitSettings",
    storages = [Storage("AICommitSettings.xml")]
)
@Service(Service.Level.APP)
class PluginSettingsState : PersistentStateComponent<PluginSettingsState> {

    var providerType: ProviderType = ProviderType.OPENAI
    var baseUrl: String = ""
    var modelName: String = DEFAULT_MODEL_OPENAI
    var promptTemplate: String = DEFAULT_PROMPT_TEMPLATE
    var maxDiffLength: Int = DEFAULT_MAX_DIFF_LENGTH

    /**
     * Returns the effective base URL - user-configured or provider default.
     */
    val effectiveBaseUrl: String
        get() = baseUrl.ifBlank { providerType.defaultBaseUrl }

    /**
     * Returns the effective model name - user-configured or provider default.
     */
    val effectiveModelName: String
        get() = modelName.ifBlank { getDefaultModelForProvider(providerType) }

    /**
     * Checks if the current provider requires an API key.
     */
    val requiresApiKey: Boolean
        get() = providerType.requiresApiKey

    override fun getState(): PluginSettingsState = this

    override fun loadState(state: PluginSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Resets all settings to their default values.
     */
    fun resetToDefaults() {
        providerType = ProviderType.OPENAI
        baseUrl = ""
        modelName = DEFAULT_MODEL_OPENAI
        promptTemplate = DEFAULT_PROMPT_TEMPLATE
        maxDiffLength = DEFAULT_MAX_DIFF_LENGTH
    }

    companion object {
        const val DEFAULT_MAX_DIFF_LENGTH = 6000
        const val DEFAULT_MODEL_OPENAI = "gpt-3.5-turbo"
        const val DEFAULT_MODEL_ANTHROPIC = "claude-3-5-sonnet-latest"
        const val DEFAULT_MODEL_OLLAMA = "llama3"

        const val DEFAULT_PROMPT_TEMPLATE = """Write a concise commit message following Conventional Commits syntax for these changes:

{diff}"""

        @JvmStatic
        fun getInstance(): PluginSettingsState {
            return ApplicationManager.getApplication().getService(PluginSettingsState::class.java)
        }

        fun getDefaultModelForProvider(provider: ProviderType): String {
            return when (provider) {
                ProviderType.OPENAI -> DEFAULT_MODEL_OPENAI
                ProviderType.ANTHROPIC -> DEFAULT_MODEL_ANTHROPIC
                ProviderType.OLLAMA -> DEFAULT_MODEL_OLLAMA
                ProviderType.CUSTOM -> DEFAULT_MODEL_OPENAI
            }
        }
    }
}
