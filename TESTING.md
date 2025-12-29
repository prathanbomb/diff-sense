# Manual Testing Checklist

## Prerequisites
1. Run `./gradlew runIde` to launch a sandbox IntelliJ instance
2. Open or create a Git-enabled project in the sandbox IDE

## Test Scenarios

### Scenario 1: Local Ollama Setup
- [ ] Go to Settings > Tools > AI Commit
- [ ] Select "Ollama" as the provider
- [ ] Verify URL shows `http://localhost:11434/v1`
- [ ] Set model to `llama3` (or your installed model)
- [ ] Leave API key blank
- [ ] Apply settings
- [ ] Make changes to a file
- [ ] Open Commit dialog (Cmd/Ctrl+K)
- [ ] Select changed files
- [ ] Click "Generate with AI" button or press Ctrl+Alt+G
- [ ] Verify commit message is generated

### Scenario 2: OpenAI Generation
- [ ] Go to Settings > Tools > AI Commit
- [ ] Select "OpenAI" as the provider
- [ ] Enter a valid OpenAI API key
- [ ] Set model to `gpt-3.5-turbo` or `gpt-4o`
- [ ] Apply settings
- [ ] Make changes to multiple files (3+)
- [ ] Open Commit dialog
- [ ] Select changed files
- [ ] Click "Generate with AI"
- [ ] Verify loading indicator appears
- [ ] Verify commit message follows Conventional Commits format

### Scenario 3: Custom Prompt Template
- [ ] Go to Settings > Tools > AI Commit
- [ ] Change the prompt template to: "Explain this like a pirate: {diff}"
- [ ] Apply settings
- [ ] Generate a commit message
- [ ] Verify the output reflects the custom prompt style

### Scenario 4: Secure Storage Verification
- [ ] Configure an OpenAI API key in settings
- [ ] Apply and close settings
- [ ] Check file: `~/.config/JetBrains/<IDE>/options/AICommitSettings.xml`
- [ ] Verify the API key is NOT present in plain text
- [ ] Re-open settings and verify the API key field shows dots (masked)

### Scenario 5: Error Handling - No Files Selected
- [ ] Open Commit dialog with NO files selected
- [ ] Click "Generate with AI"
- [ ] Verify warning notification: "No Changes Selected"

### Scenario 6: Error Handling - Missing API Key
- [ ] Select OpenAI provider
- [ ] Clear the API key field
- [ ] Apply settings
- [ ] Try to generate a commit message
- [ ] Verify error notification with "Open Settings" link

### Scenario 7: Error Handling - Invalid API Key
- [ ] Enter an invalid API key (e.g., "invalid-key")
- [ ] Apply settings
- [ ] Try to generate
- [ ] Verify 401 error notification with "Open Settings" link

### Scenario 8: Keyboard Shortcut
- [ ] Open Commit dialog with files selected
- [ ] Press Ctrl+Alt+G (or Cmd+Alt+G on Mac)
- [ ] Verify generation triggers

### Scenario 9: Diff Truncation
- [ ] Make changes to a very large file (>6000 chars of diff)
- [ ] Generate commit message
- [ ] Verify it completes without API errors
- [ ] (Optional) Check prompt size in network logs

### Scenario 10: Provider Switching
- [ ] Configure OpenAI with API key
- [ ] Switch to Anthropic
- [ ] Verify URL changes to Anthropic default
- [ ] Verify API key field is empty (keys are per-provider)
- [ ] Enter Anthropic API key
- [ ] Switch back to OpenAI
- [ ] Verify OpenAI API key is still saved

## Verification Complete
All scenarios passing indicates the plugin is ready for use.
