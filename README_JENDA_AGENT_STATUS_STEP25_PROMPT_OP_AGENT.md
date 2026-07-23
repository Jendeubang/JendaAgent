# Step 25: PromptOpAgent

## Goal

Optimize image-generation and image-edit prompts before the tool call, while preserving user intent and exposing the reasoning trail.

## Flow

```text
User prompt + selected image tool
  -> PromptKnowledgeRetriever
  -> relevant prompt guidelines
  -> PromptOpAgent + Qwen chat model
  -> prompt_optimization SSE event
  -> optimized prompt to IMAGE_GENERATE or IMAGE_EDIT
  -> COS output + Workspace
```

## Development Knowledge Base

`src/main/resources/prompt-op/image-guidelines.json` is a versioned local prompt knowledge base. The retriever ranks entries by tool type and prompt keywords. It is deliberately behind the `PromptKnowledgeRetriever` abstraction so a cloud vector database can replace it later.

## Runtime Variables

```powershell
$env:AGENT_PROMPT_OP_ENABLED="true"
$env:AGENT_PROMPT_OP_MAX_KNOWLEDGE_ITEMS="4"
$env:AGENT_PROMPT_OP_MAX_PROMPT_LENGTH="4000"
```

PromptOpAgent reuses the existing `AGENT_RUNTIME_MODEL_*` Qwen configuration. It fails open: an unavailable model or retriever returns the original prompt and never blocks image execution.

## SSE Payload

The `prompt_optimization` event contains:

- `originalPrompt`
- `optimizedPrompt`
- `retrievedRules`
- `toolTypes`
- `applied`
- `provider`

## Verification

1. Enable `AGENT_PROMPT_OP_ENABLED` in the backend terminal.
2. Run image generation or image editing from `/plan-solve` or `/image-studio`.
3. Confirm a `prompt_optimization` event appears before the image tool call.
4. Confirm the final image remains returned and archived to COS when the optimizer is temporarily disabled.
