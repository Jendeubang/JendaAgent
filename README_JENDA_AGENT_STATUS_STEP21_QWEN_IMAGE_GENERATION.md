# Step 21: Qwen Image Generation Gateway

## Goal

Enable the existing `IMAGE_GENERATE` tool through a server-side Model Studio adapter.
The browser and agent runtime call the internal Jenda endpoint only. The DashScope API key is never sent to the browser.

## Flow

```text
Plan-Solve / ReAct
  -> HttpAgentToolClient
  -> POST /internal/agent-tools/qwen-image-generate
  -> QwenImageGenerateGatewayClient
  -> Model Studio Qwen-Image synchronous API
  -> image_url
  -> tool_result + image SSE events + Workspace history
```

## Required Environment Variables

Set these in the same PowerShell window that starts Spring Boot. Do not commit real keys.

```powershell
$imageInternalKey = [Guid]::NewGuid().ToString("N") + [Guid]::NewGuid().ToString("N")

$env:AGENT_GATEWAY_IMAGE_GENERATE_ENABLED="true"
$env:AGENT_GATEWAY_IMAGE_GENERATE_ENDPOINT="https://YOUR_WORKSPACE_ID.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
$env:AGENT_GATEWAY_IMAGE_GENERATE_MODEL="qwen-image-2.0-pro"
$env:AGENT_GATEWAY_IMAGE_GENERATE_SIZE="1024*1024"
$env:AGENT_GATEWAY_IMAGE_GENERATE_PROMPT_EXTEND="true"
$env:AGENT_GATEWAY_IMAGE_GENERATE_WATERMARK="false"
$env:AGENT_GATEWAY_IMAGE_GENERATE_INTERNAL_KEY=$imageInternalKey

# Optional only when image generation uses a different Model Studio key.
# $env:AGENT_GATEWAY_IMAGE_GENERATE_API_KEY="sk-..."

$env:AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_ENABLED="true"
$env:AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_URL="http://127.0.0.1:19090/internal/agent-tools/qwen-image-generate"
$env:AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_API_KEY=$imageInternalKey
```

If `AGENT_GATEWAY_IMAGE_GENERATE_API_KEY` is absent, the adapter reuses `AGENT_RUNTIME_MODEL_API_KEY`.

## Console Prerequisites

1. In Alibaba Cloud Model Studio, use the same region as the existing Qwen key.
2. Open the business-space details page and copy its Workspace ID.
3. Ensure the Qwen Image model is available to that business space and that billing/quota is enabled.
4. Use the business-space endpoint shown above. Beijing and Singapore API keys/endpoints cannot be mixed.

## Verification

1. Restart the backend after setting variables.
2. Open `/plan-solve`.
3. Start a new session and submit: `Generate a cyberpunk city poster with neon lights and rainy streets.`
4. Expected sequence: `plan` -> `task` -> `tool_call` -> `tool_result` -> `image` -> `summary`.

## Known Limitation

Model Studio returns a temporary image URL. The current Workspace persists the URL and displays it while valid. A follow-up task will copy generated images into Tencent COS so session history remains available after the provider URL expires.
