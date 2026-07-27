# Step 63: ReAct OCR Runtime Wiring

## What changed

The ReAct runtime now receives an OCR tool route in Docker:

- Runtime route: `http://127.0.0.1:8080/internal/agent-tools/qwen-ocr`
- Runtime tool flag: `AGENT_RUNTIME_TOOLS_OCR_ENABLED=true`
- Internal authorization: reuses `AGENT_GATEWAY_IMAGE_GENERATE_INTERNAL_KEY`
- OCR gateway authorization: uses the same internal key
- Runtime timeout: 180 seconds

The browser never receives this key. Before calling Qwen, the backend downloads managed COS assets with server-side credentials and sends `data:image/...;base64` content to the model. This removes the dependency on public COS access and prevents expired signed URLs from breaking OCR.

## Existing prerequisites

The deployment environment must already have:

```dotenv
AGENT_RUNTIME_MODEL_BASE_URL=...
AGENT_RUNTIME_MODEL_API_KEY=...
AGENT_GATEWAY_OCR_ENABLED=true
AGENT_GATEWAY_IMAGE_GENERATE_INTERNAL_KEY=... 
```

No new secret is required for OCR routing. Optionally set `AGENT_GATEWAY_OCR_MODEL` when the default `qwen-vl-ocr-2025-11-20` is not enabled in the selected Bailian workspace.

## Verification

Recreate only the backend after changing Compose:

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up -d --no-deps backend
```

Confirm only presence, not secret values:

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml exec -T backend sh -lc 'echo OCR_ENABLED=$AGENT_RUNTIME_TOOLS_OCR_ENABLED; echo OCR_URL=$AGENT_RUNTIME_TOOLS_OCR_URL; test -n "$AGENT_RUNTIME_TOOLS_OCR_API_KEY" && echo OCR_KEY=SET; test -n "$AGENT_GATEWAY_OCR_INTERNAL_KEY" && echo OCR_INTERNAL_KEY=SET'
```

A completed ReAct run is immutable. Start a new image OCR request from `/zh/agent` after the backend is recreated.

## Local deployment verification

On 2026-07-27, the local production container was rebuilt and a managed COS asset was sent through the complete OCR route. The Qwen OCR gateway returned HTTP 200 with the normalized response shape:

```json
{"text":"未识别到文字","provider":"qwen-ocr"}
```

The selected generated test image contained no readable text. This confirms runtime routing, internal authentication, private COS download, Base64 conversion, Qwen invocation, and response normalization.

## Failure interpretation

- `OCR is not configured`: the runtime route variables are absent; recreate the backend with this Compose file.
- `model_not_found` or a provider `400`: enable the OCR/vision model in the Bailian workspace or set `AGENT_GATEWAY_OCR_MODEL` to an enabled compatible model.
- `timeout`: increase `AGENT_RUNTIME_TOOLS_OCR_TIMEOUT` only after confirming the provider endpoint and model are available.