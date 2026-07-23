# Step 44: Image Edit Runtime Wiring

## Root Cause

The Qwen image-edit gateway and the Agent ToolRouter use different configuration namespaces. The gateway was configured, but `agent.runtime.tools.image-edit.*` was not, so the Agent ToolRouter skipped `IMAGE_EDIT` as unavailable.

## Docker Wiring

```text
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED <- AGENT_GATEWAY_IMAGE_EDIT_ENABLED
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL     = http://127.0.0.1:8080/internal/agent-tools/qwen-image-edit
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_API_KEY <- AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY
```

The internal key is generated as a 32-byte random local secret when absent and stored only in `deploy/.env`. Do not commit that file.

## Runtime Flow

```text
Agent UI imageUrl + prompt
  -> HttpAgentToolClient (IMAGE_EDIT)
  -> internal qwen-image-edit controller
  -> Qwen image edit gateway
  -> archive result to COS
  -> SSE tool_result + image event
```

## Test

1. Open `/zh/agent`.
2. Upload a reference image.
3. Select Image Editing in Model Preference.
4. Submit a background replacement instruction.
5. Expect an image artifact event rather than `Image Editing not configured`.