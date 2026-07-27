# Step 66: ReAct Image Edit Runtime Enablement

## Problem

The deployment injected image-edit URL, API key, and timeout, but omitted `AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED`. Spring therefore bound the runtime endpoint as disabled and ReAct emitted `IMAGE_EDIT is not configured` before it called the internal gateway.

## Fix

Docker Compose now sets:

```dotenv
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED=true
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL=http://127.0.0.1:8080/internal/agent-tools/qwen-image-edit
```

The internal image-edit gateway remains backend-only. Its dedicated API key is optional; when absent it uses `AGENT_RUNTIME_MODEL_API_KEY`.

## Verification

After recreating the backend, local deployment reported:

```text
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED=true
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL=SET
AGENT_GATEWAY_IMAGE_EDIT_ENABLED=true
AGENT_GATEWAY_IMAGE_EDIT_ENDPOINT=SET
AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY=SET
```

Existing ReAct runs remain immutable. Create a new image-edit task after deployment.