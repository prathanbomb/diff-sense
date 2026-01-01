package com.prathanbomb.diffsense.providers

import com.prathanbomb.diffsense.settings.ProviderType

/**
 * AI Provider for OpenAI-compatible endpoints.
 * Works with Gemini, z.ai, Azure OpenAI, Together AI, and other OpenAI-compatible APIs.
 */
class OpenAICompatibleProvider : OpenAIProvider() {
    override val providerType: ProviderType = ProviderType.CUSTOM
    override val providerName: String = "OpenAI-compatible"
}
