# Step 23: Qwen Image Edit Gateway

## Supported Tasks

- Background replacement
- Style conversion
- Local edits, such as adding, removing, or moving objects
- Multi-image fusion with up to three reference images

## Flow

```text
User image URLs + edit prompt
  -> Plan-Solve IMAGE_EDIT task
  -> POST /internal/agent-tools/qwen-image-edit
  -> Model Studio qwen-image-2.0 synchronous API
  -> GeneratedImageCosArchiver
  -> COS signed URL
  -> tool_result + image SSE events + Workspace history
```

## Required Environment Variables

Set in the same PowerShell terminal that starts Spring Boot.

```powershell
$imageEditInternalKey = [Guid]::NewGuid().ToString("N") + [Guid]::NewGuid().ToString("N")

$env:AGENT_GATEWAY_IMAGE_EDIT_ENABLED="true"
$env:AGENT_GATEWAY_IMAGE_EDIT_ENDPOINT="https://llm-378byufvtg0fgp1u.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
$env:AGENT_GATEWAY_IMAGE_EDIT_MODEL="qwen-image-2.0"
$env:AGENT_GATEWAY_IMAGE_EDIT_SIZE="1024*1024"
$env:AGENT_GATEWAY_IMAGE_EDIT_PROMPT_EXTEND="true"
$env:AGENT_GATEWAY_IMAGE_EDIT_WATERMARK="false"
$env:AGENT_GATEWAY_IMAGE_EDIT_TIMEOUT="600s"
$env:AGENT_GATEWAY_IMAGE_EDIT_INTERNAL_KEY=$imageEditInternalKey

$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED="true"
$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_URL="http://127.0.0.1:19090/internal/agent-tools/qwen-image-edit"
$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_API_KEY=$imageEditInternalKey
$env:AGENT_RUNTIME_TOOLS_IMAGE_EDIT_TIMEOUT="600s"
```

`AGENT_GATEWAY_IMAGE_EDIT_API_KEY` is optional. If absent, the adapter reuses `AGENT_RUNTIME_MODEL_API_KEY`.

## Verification

1. Upload one reference image in `/plan-solve`.
2. Submit one of these prompts:
   - `Replace the background with a rainy cyberpunk street. Keep the person unchanged.`
   - `Convert this image to a hand-painted watercolor illustration while preserving the composition.`
   - `Remove the object in the lower-right corner and reconstruct the surrounding background naturally.`
3. Expected event sequence: `plan` -> `task` -> `tool_call` -> `tool_result` -> `image` -> `summary`.
4. A successful `tool_result` contains `Qwen image edit completed successfully; archived to COS`.

## Guardrail

The dynamic router schedules `IMAGE_EDIT` only when a reference image exists. This prevents a text-only request from entering an edit retry loop.
