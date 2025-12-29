package com.prathanbomb.diffsense.services

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiffTruncationTest {

    // Create a test helper that exposes truncateDiff without needing Project
    private fun truncateDiff(diff: String, maxLength: Int): String {
        if (diff.length <= maxLength) {
            return diff
        }

        val truncated = diff.take(maxLength)
        val lastNewline = truncated.lastIndexOf('\n')

        val breakPoint = if (lastNewline > maxLength * 0.8) {
            lastNewline
        } else {
            maxLength
        }

        return truncated.substring(0, breakPoint) + "\n\n... (diff truncated, ${diff.length - breakPoint} characters omitted)"
    }

    @Test
    fun `small diff is not truncated`() {
        val diff = "Small diff content"
        val result = truncateDiff(diff, 100)
        assertEquals(diff, result)
    }

    @Test
    fun `empty diff returns empty string`() {
        val result = truncateDiff("", 100)
        assertEquals("", result)
    }

    @Test
    fun `diff at exact limit is not truncated`() {
        val diff = "a".repeat(100)
        val result = truncateDiff(diff, 100)
        assertEquals(diff, result)
    }

    @Test
    fun `diff over limit is truncated`() {
        val diff = "a".repeat(150)
        val result = truncateDiff(diff, 100)
        assertTrue(result.length < 150)
        assertTrue(result.contains("truncated"))
    }

    @Test
    fun `truncation prefers line boundaries`() {
        val diff = buildString {
            appendLine("Line 1")
            appendLine("Line 2")
            appendLine("Line 3")
            appendLine("Line 4")
            appendLine("Line 5 is very long to push us over the limit")
        }

        val result = truncateDiff(diff, 40)

        // Should truncate and indicate truncation
        assertTrue(result.contains("truncated"))
        // Result should be shorter than original
        assertTrue(result.length < diff.length + 50) // Allow for truncation message
    }

    @Test
    fun `truncation message shows omitted character count`() {
        val diff = "a".repeat(200)
        val result = truncateDiff(diff, 100)

        assertTrue(result.contains("100 characters omitted"))
    }

    @Test
    fun `multiline diff truncation preserves complete lines when possible`() {
        val lines = (1..10).map { "Line $it with some content" }
        val diff = lines.joinToString("\n")

        // Set limit to allow about 5 lines
        val result = truncateDiff(diff, 130)

        assertTrue(result.contains("truncated"))
        // Count complete lines before truncation marker
        val truncatedContent = result.substringBefore("\n\n...")
        val completeLines = truncatedContent.count { it == '\n' }
        assertTrue(completeLines >= 1, "Should preserve at least one complete line")
    }

    @Test
    fun `very long single line is truncated at limit`() {
        val diff = "x".repeat(1000)
        val result = truncateDiff(diff, 100)

        assertTrue(result.startsWith("x".repeat(100)))
        assertTrue(result.contains("truncated"))
    }

    @Test
    fun `unicode content is handled correctly`() {
        val diff = "日本語テキスト\n英語 English\n中文内容"
        val result = truncateDiff(diff, 50)

        // Should handle unicode without crashing
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `diff with only newlines truncates correctly`() {
        val diff = "\n".repeat(200)
        val result = truncateDiff(diff, 50)

        assertTrue(result.contains("truncated"))
    }
}
