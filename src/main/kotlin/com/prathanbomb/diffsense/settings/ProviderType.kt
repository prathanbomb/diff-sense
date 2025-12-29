package com.prathanbomb.diffsense.settings

enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val requiresApiKey: Boolean
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", true),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1", true),
    OLLAMA("Ollama", "http://localhost:11434/v1", false),
    CUSTOM("Custom", "", true)
}
