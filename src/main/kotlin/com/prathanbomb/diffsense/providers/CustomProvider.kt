package com.prathanbomb.diffsense.providers

import com.prathanbomb.diffsense.settings.ProviderType

/**
 * AI Provider for custom OpenAI-compatible endpoints.
 * Extends OpenAIProvider but uses "Custom" in error messages.
 */
class CustomProvider : OpenAIProvider() {
    override val providerType: ProviderType = ProviderType.CUSTOM
    override val providerName: String = "Custom"
}
