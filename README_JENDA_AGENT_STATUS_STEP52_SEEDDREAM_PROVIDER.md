# Step 52: Pluggable Image Provider and SeedDream 4.5

## Delivered

- Added the `ImageModelProvider` contract and `ImageModelProviderRouter`.
- Wrapped the existing Qwen generation and edit clients as the `qwen` provider; current behavior remains the default.
- Added the disabled-by-default `seedream` provider for Volcano Ark text-to-image and reference-image workflows.
- Preserved existing internal endpoints, SSE output, generated-image COS archival, history, and asset metadata flows.
- Added `model_provider` to the normalized internal tool request. Omit it to use `agent.image-provider.default-provider`.
- Added per-run image-provider selection (qwen, seedream) to the Agent request, tool dispatch, and /zh/agent Model Preference popover. Selection is persisted locally; automatic mode follows the server default.

## SeedDream Environment

Set these only in uncommitted `deploy/.env` after creating a Volcano Ark API key and a SeedDream 4.5 inference endpoint:

```env
AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_ENABLED=true
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED=true
AGENT_IMAGE_PROVIDER_DEFAULT=seedream
AGENT_GATEWAY_SEEDDREAM_ENABLED=true
AGENT_GATEWAY_SEEDDREAM_ENDPOINT=https://ark.cn-beijing.volces.com/api/v3/images/generations
AGENT_GATEWAY_SEEDDREAM_MODEL=ep-your-seeddream-endpoint-id
AGENT_GATEWAY_SEEDDREAM_API_KEY=your-volcano-ark-api-key
AGENT_GATEWAY_SEEDDREAM_SIZE=2K
AGENT_GATEWAY_SEEDDREAM_WATERMARK=true
AGENT_GATEWAY_SEEDDREAM_TIMEOUT=600s
```

Use a provisioned `ep-...` endpoint ID from Volcano Ark rather than hard-coding a mutable model alias. The SeedDream adapter turns COS-owned image URLs into signed read URLs before sending reference images to Ark.

## Verification

1. Build and restart the backend stack.
2. Set `AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_ENABLED=true
AGENT_RUNTIME_TOOLS_IMAGE_EDIT_ENABLED=true
AGENT_IMAGE_PROVIDER_DEFAULT=seedream` in `deploy/.env`.
3. Submit a text-to-image request in `/zh/agent`.
4. Confirm the SSE `tool_result` says `SeedDream image generated successfully; archived to COS`.
5. Upload a reference image, select Image Editing, and confirm the equivalent edit event.

## Next Work

- Add an authenticated UI selector for individual providers, validated against server-side entitlement and health checks.
- Add NanoBanana/Gemini as another `ImageModelProvider` without changing the agent tool protocol.