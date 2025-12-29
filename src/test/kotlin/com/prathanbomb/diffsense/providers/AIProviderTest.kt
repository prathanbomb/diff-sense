package com.prathanbomb.diffsense.providers

import com.prathanbomb.diffsense.settings.ProviderType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AIProviderTest {

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
    fun `AIProvider factory creates correct provider for each type`() {
        assertTrue(AIProvider.create(ProviderType.OPENAI) is OpenAIProvider)
        assertTrue(AIProvider.create(ProviderType.ANTHROPIC) is AnthropicProvider)
        assertTrue(AIProvider.create(ProviderType.OLLAMA) is OllamaProvider)
        assertTrue(AIProvider.create(ProviderType.CUSTOM) is OpenAIProvider)
    }

    @Test
    fun `OpenAIProvider returns success for valid response`() {
        val responseJson = """
            {
                "id": "chatcmpl-123",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "feat: add new feature"
                    },
                    "finish_reason": "stop"
                }]
            }
        """.trimIndent()

        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = "test-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isSuccess)
        assertEquals("feat: add new feature", result.getOrNull())

        // Verify request
        val request = mockServer.takeRequest()
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
        assertTrue(request.path?.contains("/chat/completions") == true)
    }

    @Test
    fun `OpenAIProvider returns failure for 401 response`() {
        mockServer.enqueue(MockResponse().setResponseCode(401))

        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = "invalid-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AIProviderException
        assertEquals(401, exception.statusCode)
        assertTrue(exception.message?.contains("Invalid API key") == true)
    }

    @Test
    fun `OpenAIProvider returns failure for 429 rate limit response`() {
        mockServer.enqueue(MockResponse().setResponseCode(429))

        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = "test-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as AIProviderException
        assertEquals(429, exception.statusCode)
        assertTrue(exception.message?.contains("Rate limited") == true)
    }

    @Test
    fun `OpenAIProvider returns failure for missing API key`() {
        val provider = OpenAIProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = null,
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-3.5-turbo"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API key required") == true)
    }

    @Test
    fun `AnthropicProvider returns success for valid response`() {
        val responseJson = """
            {
                "id": "msg_123",
                "type": "message",
                "role": "assistant",
                "content": [{
                    "type": "text",
                    "text": "fix: resolve bug in parser"
                }],
                "stop_reason": "end_turn"
            }
        """.trimIndent()

        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val provider = AnthropicProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = "test-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "claude-3-5-sonnet-latest"
        )

        assertTrue(result.isSuccess)
        assertEquals("fix: resolve bug in parser", result.getOrNull())

        // Verify request headers
        val request = mockServer.takeRequest()
        assertEquals("test-key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        assertTrue(request.path?.contains("/messages") == true)
    }

    @Test
    fun `AnthropicProvider returns failure for missing API key`() {
        val provider = AnthropicProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = null,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-3-5-sonnet-latest"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API key required") == true)
    }

    @Test
    fun `OllamaProvider does not require API key`() {
        val responseJson = """
            {
                "id": "chatcmpl-123",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "chore: update dependencies"
                    },
                    "finish_reason": "stop"
                }]
            }
        """.trimIndent()

        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val provider = OllamaProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = null, // No API key
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "llama3"
        )

        assertTrue(result.isSuccess)
        assertEquals("chore: update dependencies", result.getOrNull())

        // Verify no Authorization header
        val request = mockServer.takeRequest()
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `OllamaProvider includes API key when provided`() {
        val responseJson = """
            {
                "id": "chatcmpl-123",
                "choices": [{
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "test: add unit tests"
                    },
                    "finish_reason": "stop"
                }]
            }
        """.trimIndent()

        mockServer.enqueue(MockResponse().setBody(responseJson).setResponseCode(200))

        val provider = OllamaProvider()
        val result = provider.generateCommitMessage(
            prompt = "Test prompt",
            apiKey = "optional-key",
            baseUrl = mockServer.url("/v1").toString().trimEnd('/'),
            model = "llama3"
        )

        assertTrue(result.isSuccess)

        // Verify Authorization header is present when key is provided
        val request = mockServer.takeRequest()
        assertEquals("Bearer optional-key", request.getHeader("Authorization"))
    }
}
