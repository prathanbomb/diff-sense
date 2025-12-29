package com.prathanbomb.diffsense.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class PluginSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private lateinit var providerComboBox: ComboBox<ProviderType>
    private lateinit var baseUrlField: JBTextField
    private lateinit var modelNameField: JBTextField
    private lateinit var promptTemplateArea: JBTextArea
    private lateinit var apiKeyField: JBPasswordField
    private lateinit var maxDiffLengthSpinner: JSpinner

    // Track original API keys loaded from storage (for isModified check)
    private val originalApiKeys = mutableMapOf<ProviderType, String>()

    private val settings: PluginSettingsState
        get() = PluginSettingsState.getInstance()

    private val apiKeyManager: ApiKeyManager
        get() = ApiKeyManager.getInstance()

    override fun getDisplayName(): String = "AI Commit"

    override fun createComponent(): JComponent {
        // Provider selector
        providerComboBox = ComboBox(DefaultComboBoxModel(ProviderType.entries.toTypedArray())).apply {
            renderer = ProviderTypeRenderer()
            addActionListener { onProviderChanged() }
        }

        // Base URL field
        baseUrlField = JBTextField().apply {
            emptyText.text = "Leave blank to use provider default"
        }

        // Model name field
        modelNameField = JBTextField().apply {
            emptyText.text = "Leave blank to use provider default"
        }

        // API Key field
        apiKeyField = JBPasswordField()

        // Prompt template area
        promptTemplateArea = JBTextArea(8, 50).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        val promptScrollPane = JBScrollPane(promptTemplateArea).apply {
            preferredSize = Dimension(400, 150)
        }

        // Max diff length spinner
        maxDiffLengthSpinner = JSpinner(
            SpinnerNumberModel(
                PluginSettingsState.DEFAULT_MAX_DIFF_LENGTH,
                1000,
                50000,
                500
            )
        )

        // Build the form
        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Provider:"), providerComboBox, 1, false)
            .addLabeledComponent(JBLabel("Base URL:"), baseUrlField, 1, false)
            .addLabeledComponent(JBLabel("Model:"), modelNameField, 1, false)
            .addLabeledComponent(JBLabel("API Key:"), apiKeyField, 1, false)
            .addLabeledComponent(JBLabel("Max Diff Length:"), maxDiffLengthSpinner, 1, false)
            .addSeparator()
            .addLabeledComponent(
                JBLabel("Prompt Template:"),
                promptScrollPane,
                1,
                true
            )
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply {
                border = JBUI.Borders.empty(10)
            }

        // Preload API keys asynchronously before reset()
        preloadApiKeysAndReset()

        return mainPanel!!
    }

    /**
     * Preloads all API keys from PasswordSafe asynchronously,
     * then calls reset() on the EDT to populate the UI.
     */
    private fun preloadApiKeysAndReset() {
        // Start loading all API keys in background
        apiKeyManager.preloadApiKeyAsync(settings.providerType) { _ ->
            // Once loaded, reset the UI on EDT
            resetFromCache()
        }

        // Also preload other providers in background (for switching)
        ProviderType.entries.forEach { provider ->
            if (provider != settings.providerType) {
                apiKeyManager.preloadApiKeyAsync(provider, null)
            }
        }
    }

    private fun onProviderChanged() {
        val selectedProvider = providerComboBox.selectedItem as? ProviderType ?: return
        baseUrlField.emptyText.text = "Default: ${selectedProvider.defaultBaseUrl}"
        modelNameField.emptyText.text = "Default: ${PluginSettingsState.getDefaultModelForProvider(selectedProvider)}"

        // Use cached API key (EDT-safe)
        if (apiKeyManager.isCacheLoaded(selectedProvider)) {
            val storedKey = apiKeyManager.getCachedApiKey(selectedProvider)
            apiKeyField.text = storedKey ?: ""
        } else {
            // If not cached yet, load asynchronously
            apiKeyField.text = ""
            apiKeyManager.preloadApiKeyAsync(selectedProvider) { key ->
                // Update field on EDT after loading
                if (providerComboBox.selectedItem == selectedProvider) {
                    apiKeyField.text = key ?: ""
                }
            }
        }
    }

    override fun isModified(): Boolean {
        val selectedProvider = providerComboBox.selectedItem as? ProviderType ?: return false

        return settings.providerType != selectedProvider ||
                settings.baseUrl != baseUrlField.text ||
                settings.modelName != modelNameField.text ||
                settings.promptTemplate != promptTemplateArea.text ||
                settings.maxDiffLength != (maxDiffLengthSpinner.value as Int) ||
                isApiKeyModified(selectedProvider)
    }

    private fun isApiKeyModified(provider: ProviderType): Boolean {
        // Compare against the original value we loaded, not PasswordSafe
        val originalKey = originalApiKeys[provider] ?: ""
        val newKey = String(apiKeyField.password)
        return originalKey != newKey
    }

    override fun apply() {
        val selectedProvider = providerComboBox.selectedItem as? ProviderType ?: return

        settings.providerType = selectedProvider
        settings.baseUrl = baseUrlField.text
        settings.modelName = modelNameField.text
        settings.promptTemplate = promptTemplateArea.text
        settings.maxDiffLength = maxDiffLengthSpinner.value as Int

        // Save API key securely
        val apiKey = String(apiKeyField.password)
        apiKeyManager.setApiKey(selectedProvider, apiKey.ifBlank { null })

        // Update our tracking of original values
        originalApiKeys[selectedProvider] = apiKey
    }

    override fun reset() {
        // If cache is loaded, reset from cache; otherwise trigger async load
        if (apiKeyManager.isCacheLoaded(settings.providerType)) {
            resetFromCache()
        } else {
            // Set non-API-key fields immediately
            providerComboBox.selectedItem = settings.providerType
            baseUrlField.text = settings.baseUrl
            modelNameField.text = settings.modelName
            promptTemplateArea.text = settings.promptTemplate
            maxDiffLengthSpinner.value = settings.maxDiffLength
            apiKeyField.text = ""

            // Load API key asynchronously
            apiKeyManager.preloadApiKeyAsync(settings.providerType) { key ->
                apiKeyField.text = key ?: ""
                originalApiKeys[settings.providerType] = key ?: ""
                onProviderChanged()
            }
        }
    }

    /**
     * Resets the UI from cached values (EDT-safe).
     */
    private fun resetFromCache() {
        providerComboBox.selectedItem = settings.providerType
        baseUrlField.text = settings.baseUrl
        modelNameField.text = settings.modelName
        promptTemplateArea.text = settings.promptTemplate
        maxDiffLengthSpinner.value = settings.maxDiffLength

        // Load API key from cache (EDT-safe)
        val apiKey = apiKeyManager.getCachedApiKey(settings.providerType)
        apiKeyField.text = apiKey ?: ""
        originalApiKeys[settings.providerType] = apiKey ?: ""

        onProviderChanged()
    }

    override fun disposeUIResources() {
        mainPanel = null
        originalApiKeys.clear()
    }

    /**
     * Custom renderer to display provider display names in the combo box.
     */
    private class ProviderTypeRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is ProviderType) {
                text = value.displayName
            }
            return this
        }
    }
}
