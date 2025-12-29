package com.prathanbomb.diffsense.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.prathanbomb.diffsense.settings.PluginSettingsState

@Service(Service.Level.PROJECT)
class GitDiffService(private val project: Project) {

    /**
     * Extracts and combines diff content from all provided changes.
     * Automatically truncates the result based on settings.
     *
     * @param changes Collection of VCS changes to extract diffs from
     * @return Combined diff string, potentially truncated
     */
    fun getSelectedChangesDiff(changes: Collection<Change>): String {
        if (changes.isEmpty()) {
            return ""
        }

        val combinedDiff = changes
            .mapNotNull { change -> getDiffForChange(change) }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")

        val settings = PluginSettingsState.getInstance()
        return truncateDiff(combinedDiff, settings.maxDiffLength)
    }

    /**
     * Extracts diff content for a single change.
     *
     * @param change The VCS change to extract diff from
     * @return Formatted diff string or null if unable to extract
     */
    fun getDiffForChange(change: Change): String? {
        val filePath = getFilePath(change) ?: return null

        return when (change.type) {
            Change.Type.NEW -> formatNewFile(filePath, change.afterRevision)
            Change.Type.DELETED -> formatDeletedFile(filePath, change.beforeRevision)
            Change.Type.MODIFICATION -> formatModifiedFile(filePath, change.beforeRevision, change.afterRevision)
            Change.Type.MOVED -> formatMovedFile(change)
            else -> null
        }
    }

    private fun getFilePath(change: Change): String? {
        return change.afterRevision?.file?.path
            ?: change.beforeRevision?.file?.path
    }

    private fun formatNewFile(filePath: String, afterRevision: ContentRevision?): String {
        val content = getRevisionContent(afterRevision) ?: return "New file: $filePath"

        val lines = content.lines()
        val diffLines = lines.mapIndexed { index, line ->
            "+${line}"
        }

        return buildString {
            appendLine("=== New file: $filePath ===")
            appendLine("@@ -0,0 +1,${lines.size} @@")
            diffLines.forEach { appendLine(it) }
        }.trimEnd()
    }

    private fun formatDeletedFile(filePath: String, beforeRevision: ContentRevision?): String {
        val content = getRevisionContent(beforeRevision) ?: return "Deleted file: $filePath"

        val lines = content.lines()
        val diffLines = lines.map { line -> "-$line" }

        return buildString {
            appendLine("=== Deleted file: $filePath ===")
            appendLine("@@ -1,${lines.size} +0,0 @@")
            diffLines.forEach { appendLine(it) }
        }.trimEnd()
    }

    private fun formatModifiedFile(
        filePath: String,
        beforeRevision: ContentRevision?,
        afterRevision: ContentRevision?
    ): String {
        val beforeContent = getRevisionContent(beforeRevision) ?: ""
        val afterContent = getRevisionContent(afterRevision) ?: ""

        if (beforeContent == afterContent) {
            return "" // No actual changes
        }

        val diff = computeUnifiedDiff(beforeContent, afterContent)

        return buildString {
            appendLine("=== Modified file: $filePath ===")
            append(diff)
        }.trimEnd()
    }

    private fun formatMovedFile(change: Change): String {
        val beforePath = change.beforeRevision?.file?.path ?: "unknown"
        val afterPath = change.afterRevision?.file?.path ?: "unknown"

        val beforeContent = getRevisionContent(change.beforeRevision) ?: ""
        val afterContent = getRevisionContent(change.afterRevision) ?: ""

        return if (beforeContent == afterContent) {
            "=== Moved file: $beforePath -> $afterPath ==="
        } else {
            val diff = computeUnifiedDiff(beforeContent, afterContent)
            buildString {
                appendLine("=== Moved and modified file: $beforePath -> $afterPath ===")
                append(diff)
            }.trimEnd()
        }
    }

    private fun getRevisionContent(revision: ContentRevision?): String? {
        return try {
            revision?.content
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Computes a unified diff between two contents.
     * Uses an optimized algorithm that only outputs changed lines.
     */
    private fun computeUnifiedDiff(before: String, after: String): String {
        // Early exit for identical content
        if (before == after) return ""

        // Early exit for empty before (all additions)
        if (before.isEmpty()) {
            return after.lines().joinToString("\n") { "+$it" }
        }

        // Early exit for empty after (all deletions)
        if (after.isEmpty()) {
            return before.lines().joinToString("\n") { "-$it" }
        }

        val beforeLines = before.lines()
        val afterLines = after.lines()

        // Build set of after lines for O(1) lookup
        val afterLineSet = afterLines.toSet()
        val beforeLineSet = beforeLines.toSet()

        val result = StringBuilder()

        // Output deleted lines (in before but not in after)
        beforeLines.forEach { line ->
            if (line !in afterLineSet) {
                result.appendLine("-$line")
            }
        }

        // Output added lines (in after but not in before)
        afterLines.forEach { line ->
            if (line !in beforeLineSet) {
                result.appendLine("+$line")
            }
        }

        return result.toString().trimEnd()
    }

    /**
     * Truncates the diff to the specified maximum length.
     * Tries to truncate at line boundaries when possible.
     *
     * @param diff The diff string to truncate
     * @param maxLength Maximum allowed length
     * @return Truncated diff with indicator if truncation occurred
     */
    fun truncateDiff(diff: String, maxLength: Int): String {
        if (diff.length <= maxLength) {
            return diff
        }

        // Try to find a good break point (end of a line)
        val truncated = diff.take(maxLength)
        val lastNewline = truncated.lastIndexOf('\n')

        val breakPoint = if (lastNewline > maxLength * 0.8) {
            // Only use the newline if it's reasonably close to the end
            lastNewline
        } else {
            maxLength
        }

        return truncated.substring(0, breakPoint) + "\n\n... (diff truncated, ${diff.length - breakPoint} characters omitted)"
    }

    private data class DiffHunk(
        val beforeStart: Int,
        val beforeCount: Int,
        val afterStart: Int,
        val afterCount: Int,
        val lines: List<String>
    )

    companion object {
        fun getInstance(project: Project): GitDiffService {
            return project.service<GitDiffService>()
        }
    }
}
