# DiffSense - AI Commit Assistant

An IntelliJ IDEA plugin that automatically generates Git commit messages using AI/LLM providers.

## Features

- Generate commit messages from staged changes with one click
- Support for multiple AI providers:
  - OpenAI (GPT-3.5, GPT-4)
  - Anthropic (Claude)
  - Ollama (local models)
  - Custom OpenAI-compatible APIs
- Customizable prompt templates
- Secure API key storage using system keychain
- Configurable diff truncation
- Test Connection button to verify API configuration
- Smart button state - automatically disabled when no changes selected or API key missing

## Installation

### From Source

1. Clone the repository:
   ```bash
   git clone https://github.com/prathanbomb/diff-sense.git
   cd diff-sense
   ```

2. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```

3. Install the plugin:
   - Go to **Settings > Plugins > Gear Icon > Install Plugin from Disk**
   - Select `build/distributions/diff-sense-*.zip`

### Development

Run the plugin in a sandbox IDE:
```bash
./gradlew runIde
```

## Usage

1. **Configure the plugin:**
   - Go to **Settings > Tools > AI Commit**
   - Select your AI provider
   - Enter your API key (if required)
   - Optionally customize the base URL and model

2. **Generate commit messages:**
   - Open the Commit dialog (`Cmd/Ctrl+K`)
   - Select the files you want to commit
   - Click **"Generate with AI"** or press `Ctrl+Alt+G`

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| Provider | AI provider to use | OpenAI |
| Base URL | API endpoint (leave blank for default) | Provider default |
| Model | Model name | Provider default |
| API Key | Your API key (stored securely) | - |
| Max Diff Length | Maximum characters of diff to send | 6000 |
| Prompt Template | Custom prompt with `{diff}` placeholder | Conventional Commits |

### Provider Defaults

| Provider | Default URL | Default Model |
|----------|-------------|---------------|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` |
| Anthropic | `https://api.anthropic.com/v1` | `claude-3-5-sonnet-latest` |
| Ollama | `http://localhost:11434/v1` | `llama3` |
| OpenAI-compatible | - | - |

## Requirements

- IntelliJ IDEA 2023.3 - 2025.3
- Git integration enabled

## License

MIT

## Contributing

Contributions are welcome! Please open an issue or submit a pull request.
