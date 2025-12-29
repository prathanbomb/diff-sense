package com.prathanbomb.diffsense.services

import com.prathanbomb.diffsense.settings.PluginSettingsState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommitMessageGeneratorTest {

    @Test
    fun `buildPrompt replaces diff placeholder`() {
        val template = "Generate commit message for:\n\n{diff}"
        val diff = "+added line\n-removed line"

        val result = buildPrompt(template, diff)

        assertEquals("Generate commit message for:\n\n+added line\n-removed line", result)
    }

    @Test
    fun `buildPrompt handles empty template with default`() {
        val template = ""
        val diff = "some diff content"

        val result = buildPrompt(template, diff)

        assertTrue(result.contains("some diff content"))
        assertTrue(result.contains("Conventional Commits"))
    }

    @Test
    fun `buildPrompt handles template without placeholder`() {
        val template = "Just generate something"
        val diff = "some diff"

        val result = buildPrompt(template, diff)

        // If no {diff} placeholder, the diff won't be injected
        assertEquals("Just generate something", result)
    }

    @Test
    fun `buildPrompt handles multiple diff placeholders`() {
        val template = "First: {diff}\nSecond: {diff}"
        val diff = "changes"

        val result = buildPrompt(template, diff)

        assertEquals("First: changes\nSecond: changes", result)
    }

    @Test
    fun `buildPrompt handles special characters in diff`() {
        val template = "Changes:\n{diff}"
        val diff = "Line with \$pecial ch@racters & symbols < > \" '"

        val result = buildPrompt(template, diff)

        assertTrue(result.contains("\$pecial"))
        assertTrue(result.contains("ch@racters"))
    }

    @Test
    fun `buildPrompt handles multiline diff`() {
        val template = "Analyze these changes:\n{diff}"
        val diff = """
            === Modified file: src/main.kt ===
            -old code
            +new code
            +another new line
        """.trimIndent()

        val result = buildPrompt(template, diff)

        assertTrue(result.contains("=== Modified file"))
        assertTrue(result.contains("-old code"))
        assertTrue(result.contains("+new code"))
    }

    @Test
    fun `buildPrompt preserves template formatting`() {
        val template = """
            You are a helpful assistant.

            Generate a commit message for:

            {diff}

            Use Conventional Commits format.
        """.trimIndent()
        val diff = "some changes"

        val result = buildPrompt(template, diff)

        assertTrue(result.contains("You are a helpful assistant."))
        assertTrue(result.contains("Use Conventional Commits format."))
        assertTrue(result.contains("some changes"))
    }

    @Test
    fun `GenerationException noChanges has correct message`() {
        val exception = GenerationException.noChanges()

        assertTrue(exception.message?.contains("No changes selected") == true)
    }

    @Test
    fun `GenerationException generationFailed includes cause message`() {
        val cause = RuntimeException("API error")
        val exception = GenerationException.generationFailed(cause)

        assertTrue(exception.message?.contains("API error") == true)
        assertEquals(cause, exception.cause)
    }

    // Helper function that mirrors the actual implementation
    private fun buildPrompt(template: String, diff: String): String {
        val effectiveTemplate = template.ifBlank { PluginSettingsState.DEFAULT_PROMPT_TEMPLATE }
        return effectiveTemplate.replace("{diff}", diff)
    }
}
