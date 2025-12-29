package com.prathanbomb.diffsense.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PluginSettingsStateTest {

    private lateinit var settings: PluginSettingsState

    @BeforeEach
    fun setUp() {
        settings = PluginSettingsState()
    }

    @Test
    fun `default values are set correctly`() {
        assertEquals(ProviderType.OPENAI, settings.providerType)
        assertEquals("", settings.baseUrl)
        assertEquals(PluginSettingsState.DEFAULT_MODEL_OPENAI, settings.modelName)
        assertEquals(PluginSettingsState.DEFAULT_PROMPT_TEMPLATE, settings.promptTemplate)
        assertEquals(PluginSettingsState.DEFAULT_MAX_DIFF_LENGTH, settings.maxDiffLength)
    }

    @Test
    fun `effectiveBaseUrl returns provider default when baseUrl is blank`() {
        settings.providerType = ProviderType.OPENAI
        settings.baseUrl = ""
        assertEquals("https://api.openai.com/v1", settings.effectiveBaseUrl)

        settings.providerType = ProviderType.ANTHROPIC
        assertEquals("https://api.anthropic.com/v1", settings.effectiveBaseUrl)

        settings.providerType = ProviderType.OLLAMA
        assertEquals("http://localhost:11434/v1", settings.effectiveBaseUrl)
    }

    @Test
    fun `effectiveBaseUrl returns custom URL when set`() {
        settings.providerType = ProviderType.OPENAI
        settings.baseUrl = "https://custom.api.com/v1"
        assertEquals("https://custom.api.com/v1", settings.effectiveBaseUrl)
    }

    @Test
    fun `effectiveModelName returns provider default when modelName is blank`() {
        settings.modelName = ""

        settings.providerType = ProviderType.OPENAI
        assertEquals(PluginSettingsState.DEFAULT_MODEL_OPENAI, settings.effectiveModelName)

        settings.providerType = ProviderType.ANTHROPIC
        assertEquals(PluginSettingsState.DEFAULT_MODEL_ANTHROPIC, settings.effectiveModelName)

        settings.providerType = ProviderType.OLLAMA
        assertEquals(PluginSettingsState.DEFAULT_MODEL_OLLAMA, settings.effectiveModelName)
    }

    @Test
    fun `effectiveModelName returns custom model when set`() {
        settings.modelName = "gpt-4o"
        assertEquals("gpt-4o", settings.effectiveModelName)
    }

    @Test
    fun `requiresApiKey reflects provider setting`() {
        settings.providerType = ProviderType.OPENAI
        assertTrue(settings.requiresApiKey)

        settings.providerType = ProviderType.ANTHROPIC
        assertTrue(settings.requiresApiKey)

        settings.providerType = ProviderType.OLLAMA
        assertFalse(settings.requiresApiKey)

        settings.providerType = ProviderType.CUSTOM
        assertTrue(settings.requiresApiKey)
    }

    @Test
    fun `resetToDefaults restores all default values`() {
        // Change all values
        settings.providerType = ProviderType.ANTHROPIC
        settings.baseUrl = "https://custom.url"
        settings.modelName = "custom-model"
        settings.promptTemplate = "Custom template"
        settings.maxDiffLength = 1000

        // Reset
        settings.resetToDefaults()

        // Verify defaults
        assertEquals(ProviderType.OPENAI, settings.providerType)
        assertEquals("", settings.baseUrl)
        assertEquals(PluginSettingsState.DEFAULT_MODEL_OPENAI, settings.modelName)
        assertEquals(PluginSettingsState.DEFAULT_PROMPT_TEMPLATE, settings.promptTemplate)
        assertEquals(PluginSettingsState.DEFAULT_MAX_DIFF_LENGTH, settings.maxDiffLength)
    }

    @Test
    fun `getState returns self`() {
        assertSame(settings, settings.state)
    }

    @Test
    fun `loadState copies values from another state`() {
        val otherState = PluginSettingsState().apply {
            providerType = ProviderType.ANTHROPIC
            baseUrl = "https://other.url"
            modelName = "other-model"
            promptTemplate = "Other template"
            maxDiffLength = 5000
        }

        settings.loadState(otherState)

        assertEquals(ProviderType.ANTHROPIC, settings.providerType)
        assertEquals("https://other.url", settings.baseUrl)
        assertEquals("other-model", settings.modelName)
        assertEquals("Other template", settings.promptTemplate)
        assertEquals(5000, settings.maxDiffLength)
    }

    @Test
    fun `getDefaultModelForProvider returns correct defaults`() {
        assertEquals(
            PluginSettingsState.DEFAULT_MODEL_OPENAI,
            PluginSettingsState.getDefaultModelForProvider(ProviderType.OPENAI)
        )
        assertEquals(
            PluginSettingsState.DEFAULT_MODEL_ANTHROPIC,
            PluginSettingsState.getDefaultModelForProvider(ProviderType.ANTHROPIC)
        )
        assertEquals(
            PluginSettingsState.DEFAULT_MODEL_OLLAMA,
            PluginSettingsState.getDefaultModelForProvider(ProviderType.OLLAMA)
        )
        assertEquals(
            PluginSettingsState.DEFAULT_MODEL_OPENAI,
            PluginSettingsState.getDefaultModelForProvider(ProviderType.CUSTOM)
        )
    }
}
