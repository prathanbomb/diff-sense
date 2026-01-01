package com.prathanbomb.diffsense.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.vcs.commit.CommitWorkflowUi
import com.prathanbomb.diffsense.services.CommitMessageGenerator
import com.prathanbomb.diffsense.services.GenerationException
import com.prathanbomb.diffsense.services.GitDiffService
import com.prathanbomb.diffsense.settings.ApiKeyManager
import com.prathanbomb.diffsense.settings.PluginSettingsState
import com.prathanbomb.diffsense.util.NotificationHelper

/**
 * Action to generate a commit message using AI.
 * Appears in the commit dialog toolbar.
 */
class GenerateCommitMessageAction : AnAction() {

    companion object {
        @Volatile
        private var isGenerating = false
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get selected changes from commit dialog
        val changes = getSelectedChanges(e)
        if (changes.isNullOrEmpty()) {
            NotificationHelper.showNoChangesWarning(project)
            return
        }

        // Get the commit message editor
        val commitMessageUi = getCommitMessageUi(e)

        // Run generation in background
        generateCommitMessageAsync(project, changes, commitMessageUi)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val changes = getSelectedChanges(e)
        val settings = PluginSettingsState.getInstance()
        val apiKeyManager = ApiKeyManager.getInstance()

        // Check if API key is required but missing
        // Be optimistic if cache not loaded yet - actual validation happens on click
        val hasRequiredApiKey = if (settings.providerType.requiresApiKey) {
            !apiKeyManager.isCacheLoaded(settings.providerType) ||
                !apiKeyManager.getCachedApiKey(settings.providerType).isNullOrBlank()
        } else {
            true // Ollama doesn't require API key
        }

        // Always show button in commit context
        e.presentation.isVisible = project != null

        // Enable only when changes selected, not generating, and API key configured
        e.presentation.isEnabled = !changes.isNullOrEmpty() && !isGenerating && hasRequiredApiKey
    }

    /**
     * Gets the selected changes from the commit dialog.
     */
    private fun getSelectedChanges(e: AnActionEvent): Collection<Change>? {
        // Try to get changes from the commit workflow
        val commitWorkflowUi = e.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) as? CommitWorkflowUi
        if (commitWorkflowUi != null) {
            val includedChanges = commitWorkflowUi.getIncludedChanges()
            if (includedChanges.isNotEmpty()) {
                return includedChanges
            }
        }

        // Fallback: try to get from VCS data keys
        return e.getData(VcsDataKeys.CHANGES)?.toList()
    }

    /**
     * Gets the commit message UI component.
     */
    private fun getCommitMessageUi(e: AnActionEvent): CommitMessage? {
        return e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
    }

    /**
     * Runs commit message generation asynchronously.
     */
    private fun generateCommitMessageAsync(
        project: Project,
        changes: Collection<Change>,
        commitMessageUi: CommitMessage?
    ) {
        isGenerating = true

        object : Task.Backgroundable(project, "Generating commit message...", true) {
            private var generatedMessage: String? = null
            private var error: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing changes..."

                try {
                    // Extract diff from changes
                    val diffService = GitDiffService.getInstance(project)
                    val diff = diffService.getSelectedChangesDiff(changes)

                    if (diff.isBlank()) {
                        error = GenerationException.noChanges()
                        return
                    }

                    indicator.text = "Generating commit message..."

                    // Generate commit message
                    val generator = CommitMessageGenerator.getInstance()
                    val result = generator.generate(diff)

                    result.fold(
                        onSuccess = { message ->
                            generatedMessage = message
                        },
                        onFailure = { throwable ->
                            error = throwable
                        }
                    )
                } catch (e: Exception) {
                    error = e
                }
            }

            override fun onSuccess() {
                generatedMessage?.let { message ->
                    setCommitMessage(commitMessageUi, message)
                    NotificationHelper.showGenerationSuccess(project)
                }
            }

            override fun onThrowable(error: Throwable) {
                NotificationHelper.showErrorForException(project, error)
            }

            override fun onFinished() {
                isGenerating = false
                error?.let { NotificationHelper.showErrorForException(project, it) }
            }
        }.queue()
    }

    /**
     * Sets the generated message in the commit message editor.
     */
    private fun setCommitMessage(commitMessageUi: CommitMessage?, message: String) {
        ApplicationManager.getApplication().invokeLater {
            commitMessageUi?.setCommitMessage(message)
        }
    }
}
