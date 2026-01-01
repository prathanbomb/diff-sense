package com.prathanbomb.diffsense.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.prathanbomb.diffsense.providers.AIProviderException
import com.prathanbomb.diffsense.services.GenerationException
import com.prathanbomb.diffsense.settings.PluginSettingsConfigurable

/**
 * Utility class for showing notifications in the IDE.
 */
object NotificationHelper {

    private const val NOTIFICATION_GROUP = "AI Commit Assistant"

    /**
     * Shows an informational notification.
     */
    fun showInfo(project: Project?, message: String, title: String? = null) {
        createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * Shows a warning notification.
     */
    fun showWarning(project: Project?, message: String, title: String? = null) {
        createNotification(title, message, NotificationType.WARNING)
            .notify(project)
    }

    /**
     * Shows an error notification.
     */
    fun showError(project: Project?, message: String, title: String? = null) {
        createNotification(title, message, NotificationType.ERROR)
            .notify(project)
    }

    /**
     * Shows an error notification with a link to open settings.
     */
    fun showErrorWithSettingsLink(project: Project, message: String, title: String? = null) {
        val notification = createNotification(title, message, NotificationType.ERROR)
        notification.addAction(createOpenSettingsAction(project, notification))
        notification.notify(project)
    }

    /**
     * Shows an appropriate notification based on the error type.
     */
    fun showErrorForException(project: Project, error: Throwable) {
        when (error) {
            is AIProviderException -> handleAIProviderException(project, error)
            is GenerationException -> showError(project, error.message ?: "Generation failed", "Generation Error")
            else -> showError(project, error.message ?: "An unexpected error occurred", "Error")
        }
    }

    /**
     * Handles AI provider specific exceptions.
     */
    private fun handleAIProviderException(project: Project, error: AIProviderException) {
        val message = error.message ?: "Unknown AI provider error"

        when {
            error.statusCode == 401 -> {
                showErrorWithSettingsLink(
                    project,
                    message,
                    "Authentication Error"
                )
            }
            error.statusCode == 429 -> {
                showWarning(
                    project,
                    message,
                    "Rate Limited"
                )
            }
            error.message?.contains("API key required") == true -> {
                showErrorWithSettingsLink(
                    project,
                    message,
                    "Configuration Required"
                )
            }
            error.statusCode in 500..599 -> {
                showError(
                    project,
                    message,
                    "Server Error"
                )
            }
            else -> {
                showError(
                    project,
                    message,
                    "AI Provider Error"
                )
            }
        }
    }

    /**
     * Shows a notification for missing API key.
     */
    fun showMissingApiKeyError(project: Project, providerName: String) {
        showErrorWithSettingsLink(
            project,
            "API key required for $providerName. Please configure it in Settings.",
            "Configuration Required"
        )
    }

    /**
     * Shows a notification for no changes selected.
     */
    fun showNoChangesWarning(project: Project) {
        showWarning(
            project,
            "Please select files to include in the commit.",
            "No Changes Selected"
        )
    }

    /**
     * Shows a success notification for generated message.
     */
    fun showGenerationSuccess(project: Project) {
        showInfo(project, "Commit message generated successfully")
    }

    /**
     * Shows a notification for network errors.
     */
    fun showNetworkError(project: Project, details: String? = null) {
        val message = if (details != null) {
            "Network error: $details"
        } else {
            "Network error. Please check your connection and try again."
        }
        showError(project, message, "Connection Error")
    }

    private fun createNotification(
        title: String?,
        content: String,
        type: NotificationType
    ): Notification {
        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)

        return if (title != null) {
            group.createNotification(title, content, type)
        } else {
            group.createNotification(content, type)
        }
    }

    private fun createOpenSettingsAction(project: Project, notification: Notification): NotificationAction {
        return NotificationAction.createSimple("Open Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                PluginSettingsConfigurable::class.java
            )
            notification.expire()
        }
    }
}
