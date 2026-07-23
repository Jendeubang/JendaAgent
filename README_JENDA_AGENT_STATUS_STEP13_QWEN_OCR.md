# Step 13 Qwen OCR Tool Gateway

## Architecture

The existing `HttpAgentToolClient` already sends normalized tool requests. This
step adds an internal Spring endpoint that converts that request into a
DashScope OpenAI-compatible Qwen OCR request.

```text
ExecutorAgent -> HttpAgentToolClient -> /internal/agent-tools/qwen-ocr
    -> QwenOcrGatewayClient -> DashScope qwen-vl-ocr-2025-11-20
    -> { text } -> tool_result SSE event
```

The same Model Studio API key used for PlanningAgent is kept server-side. The
internal endpoint has its own shared key so it cannot be used as an unauthenticated
public OCR proxy.

## Required Environment Variables

```powershell
$env:AGENT_GATEWAY_OCR_ENABLED="true"
$env:AGENT_GATEWAY_OCR_MODEL="qwen-vl-ocr-2025-11-20"
$env:AGENT_GATEWAY_OCR_INTERNAL_KEY="replace-with-a-long-random-local-secret"
$env:AGENT_GATEWAY_OCR_TIMEOUT="180s"

$env:AGENT_RUNTIME_TOOLS_OCR_ENABLED="true"
$env:AGENT_RUNTIME_TOOLS_OCR_URL="http://127.0.0.1:19090/internal/agent-tools/qwen-ocr"
$env:AGENT_RUNTIME_TOOLS_OCR_API_KEY=$env:AGENT_GATEWAY_OCR_INTERNAL_KEY
```

Keep the existing `AGENT_RUNTIME_MODEL_BASE_URL` and
`AGENT_RUNTIME_MODEL_API_KEY` configuration. The OCR model must be available
to that Model Studio workspace/API key.

## Validation

Use `/live-multimodal`, upload a text-containing image, then send:

```text
识别图片中的全部文字，并保留换行。
```

Expected SSE: `run_started`, `plan`, `task` (OCR), `tool_result` (Qwen OCR
text), `summary`, `run_completed`.

## Follow-up

Add a dedicated `tool_call` event before invoking the adapter, then implement
Wan image generation and editing adapters with the same normalized-tool pattern.
