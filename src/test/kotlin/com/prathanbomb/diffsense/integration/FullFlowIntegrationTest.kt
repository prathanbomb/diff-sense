package com.prathanbomb.diffsense.integration

import com.prathanbomb.diffsense.providers.AIProvider
import com.prathanbomb.diffsense.providers.AnthropicProvider
import com.prathanbomb.diffsense.providers.CustomProvider
import com.prathanbomb.diffsense.providers.OllamaProvider
import com.prathanbomb.diffsense.providers.OpenAIProvider
import com.prathanbomb.diffsense.settings.PluginSettingsState
import com.prathanbomb.diffsense.settings.ProviderType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration tests that verify the full generation flow with mocked HTTP.
 */
class FullFlowIntegrationTest {

    private lateinit var mockServer: MockWebServer

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `full flow with OpenAI provider generates commit message`() {
        // Setup mock response
        val responseJson = """
            {
                "id": "chatcmpl-123",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "feat(auth): add user authentication flow\n\nImplement login and logout functionality with session management."
                    },
                    "finish_reason": "stop"
                }]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        // Build prompt
        val diff = """
            === New file: src/auth/login.kt ===
            @@ -0,0 +1,20 @@
            +class LoginService {
            +    fun login(username: String, password: String): Boolean {
            +        // Authentication logic
            +        return true
            +    }
            +}
        """.trimIndent()

        val prompt = buildPrompt(PluginSettingsState.DEFAULT_PROMPT_TEMPLATE, diff)

        // Generate
        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = prompt,
            apiKey = "test-api-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        // Verify
        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!
        assertTrue(message.contains("feat"))
        assertTrue(message.contains("auth"))

        // Verify request was sent correctly
        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path?.contains("/chat/completions") == true)
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("gpt-3.5-turbo"))
        assertTrue(body.contains("LoginService"))
    }

    @Test
    fun `full flow with Anthropic provider generates commit message`() {
        val responseJson = """
            {
                "id": "msg_123",
                "type": "message",
                "role": "assistant",
                "content": [{
                    "type": "text",
                    "text": "fix(parser): resolve null pointer exception in JSON parsing"
                }],
                "stop_reason": "end_turn"
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val diff = """
            === Modified file: src/parser/JsonParser.kt ===
            -val data = json.parse()
            +val data = json?.parse() ?: emptyMap()
        """.trimIndent()

        val prompt = buildPrompt(PluginSettingsState.DEFAULT_PROMPT_TEMPLATE, diff)

        val provider = AnthropicProvider()
        val result = provider.generateCommitMessage(
            prompt = prompt,
            apiKey = "anthropic-api-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "claude-3-5-sonnet-latest"
        )

        assertTrue(result.isSuccess)
        val message = result.getOrNull()!!
        assertTrue(message.contains("fix"))

        val request = mockServer.takeRequest()
        assertEquals("anthropic-api-key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
    }

    @Test
    fun `full flow with Ollama provider works without API key`() {
        val responseJson = """
            {
                "id": "chatcmpl-local",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "chore: update dependencies to latest versions"
                    },
                    "finish_reason": "stop"
                }]
            }
        """.trimIndent()
        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val diff = """
            === Modified file: build.gradle.kts ===
            -implementation("com.squareup.okhttp3:okhttp:4.11.0")
            +implementation("com.squareup.okhttp3:okhttp:4.12.0")
        """.trimIndent()

        val prompt = buildPrompt(PluginSettingsState.DEFAULT_PROMPT_TEMPLATE, diff)

        val provider = OllamaProvider()
        val result = provider.generateCommitMessage(
            prompt = prompt,
            apiKey = null, // No API key needed
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "llama3"
        )

        assertTrue(result.isSuccess)
        assertEquals("chore: update dependencies to latest versions", result.getOrNull())

        val request = mockServer.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `provider factory creates correct instances`() {
        assertTrue(AIProvider.create(ProviderType.OPENAI) is OpenAIProvider)
        assertTrue(AIProvider.create(ProviderType.ANTHROPIC) is AnthropicProvider)
        assertTrue(AIProvider.create(ProviderType.OLLAMA) is OllamaProvider)
        assertTrue(AIProvider.create(ProviderType.CUSTOM) is CustomProvider)
    }

    @Test
    fun `settings state provides correct effective values`() {
        val settings = PluginSettingsState()

        // Test default effective URL
        settings.providerType = ProviderType.OPENAI
        settings.baseUrl = ""
        assertEquals("https://api.openai.com/v1", settings.effectiveBaseUrl)

        // Test custom URL overrides default
        settings.baseUrl = "https://custom.api.com/v1"
        assertEquals("https://custom.api.com/v1", settings.effectiveBaseUrl)

        // Test effective model for different providers
        settings.modelName = ""
        settings.providerType = ProviderType.OPENAI
        assertEquals(PluginSettingsState.DEFAULT_MODEL_OPENAI, settings.effectiveModelName)

        settings.providerType = ProviderType.ANTHROPIC
        assertEquals(PluginSettingsState.DEFAULT_MODEL_ANTHROPIC, settings.effectiveModelName)

        settings.providerType = ProviderType.OLLAMA
        assertEquals(PluginSettingsState.DEFAULT_MODEL_OLLAMA, settings.effectiveModelName)
    }

    @Test
    fun `prompt template substitution works correctly`() {
        val template = PluginSettingsState.DEFAULT_PROMPT_TEMPLATE
        val diff = "+new line of code"

        val result = buildPrompt(template, diff)

        assertTrue(result.contains("Conventional Commits"))
        assertTrue(result.contains("+new line of code"))
        assertFalse(result.contains("{diff}"))
    }

    @Test
    fun `custom prompt template is used when provided`() {
        val customTemplate = "You are a pirate. Describe these changes:\n\n{diff}"
        val diff = "+added treasure map"

        val result = buildPrompt(customTemplate, diff)

        assertTrue(result.contains("pirate"))
        assertTrue(result.contains("+added treasure map"))
    }

    @Test
    fun `error handling for 401 unauthorized`() {
        mockServer.enqueue(MockResponse().setResponseCode(401))

        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "test",
            apiKey = "invalid-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid API key") == true)
    }

    @Test
    fun `error handling for 429 rate limit`() {
        mockServer.enqueue(MockResponse().setResponseCode(429))

        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "test",
            apiKey = "valid-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Rate limited") == true)
    }

    @Test
    fun `error handling for 500 server error`() {
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "test",
            apiKey = "valid-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Server error") == true ||
                result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `provider type has correct metadata`() {
        assertEquals("OpenAI", ProviderType.OPENAI.displayName)
        assertEquals("https://api.openai.com/v1", ProviderType.OPENAI.defaultBaseUrl)
        assertTrue(ProviderType.OPENAI.requiresApiKey)

        assertEquals("Anthropic", ProviderType.ANTHROPIC.displayName)
        assertEquals("https://api.anthropic.com/v1", ProviderType.ANTHROPIC.defaultBaseUrl)
        assertTrue(ProviderType.ANTHROPIC.requiresApiKey)

        assertEquals("Ollama", ProviderType.OLLAMA.displayName)
        assertEquals("http://localhost:11434/v1", ProviderType.OLLAMA.defaultBaseUrl)
        assertFalse(ProviderType.OLLAMA.requiresApiKey)
    }

    // Helper function
    private fun buildPrompt(template: String, diff: String): String {
        val effectiveTemplate = template.ifBlank { PluginSettingsState.DEFAULT_PROMPT_TEMPLATE }
        return effectiveTemplate.replace("{diff}", diff)
    }
}
