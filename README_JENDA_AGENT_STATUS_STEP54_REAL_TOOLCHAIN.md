# Step 54: Real Image Toolchain

## Implemented

- Added authenticated `POST /api/v1/agent/sessions/{sessionId}/tools/{toolId}/runs` SSE endpoint.
- Added one workflow lifecycle for upload asset input, task event, tool call, provider result, COS asset, MySQL metadata, history replay, and summary.
- Added configurable providers for `image-upscale`, `seedvr2`, and `image-layered`.
- Added configurable image workflows for `product-refinement`, `product-detail-image`, `ecommerce-promotion-poster`, `character-setting-sheet`, and `emoji-sticker`.
- Creative and ecommerce workflows can fall back to the existing Qwen/Gemini/SeedDream image provider when `agent.tools.fallback-to-image-provider=true`.
- Upscale, SeedVR2, and layering never pretend to succeed when their dedicated endpoint is not configured.
- Frontend `/zh/tool/[slug]` now uploads to the authenticated media API, calls the real SSE endpoint, renders task events, and displays the archived result image.

## Environment variables

Each tool uses the same fields. Replace the uppercase tool name with the mapping below.

| Tool | Prefix |
|---|---|
| Image Upscale | `AGENT_TOOLS_IMAGE_UPSCALE_` |
| SeedVR2 | `AGENT_TOOLS_SEEDVR2_` |
| Image Layered | `AGENT_TOOLS_IMAGE_LAYERED_` |
| Product Refinement | `AGENT_TOOLS_PRODUCT_REFINEMENT_` |
| Product Detail Image | `AGENT_TOOLS_PRODUCT_DETAIL_IMAGE_` |
| Ecommerce Poster | `AGENT_TOOLS_ECOMMERCE_PROMOTION_POSTER_` |
| Character Sheet | `AGENT_TOOLS_CHARACTER_SETTING_SHEET_` |
| Emoji Sticker | `AGENT_TOOLS_EMOJI_STICKER_` |

Example:

```powershell
$env:AGENT_TOOLS_SEEDVR2_ENABLED="true"
$env:AGENT_TOOLS_SEEDVR2_ENDPOINT="https://your-provider.example/v1/process"
$env:AGENT_TOOLS_SEEDVR2_API_KEY="replace-me"
$env:AGENT_TOOLS_SEEDVR2_MODEL="seedvr2"
$env:AGENT_TOOLS_SEEDVR2_RESULT_HOST_SUFFIXES=".example"
$env:AGENT_TOOLS_SEEDVR2_TIMEOUT="PT10M"
```

`RESULT_HOST_SUFFIXES` is required when the provider returns an image URL from a different host than the API endpoint. Use a narrow suffix such as `.replicate.delivery`; do not use `*`.

## Provider request contract

The configurable provider receives JSON:

```json
{
  "model": "seedvr2",
  "prompt": "...",
  "image_url": "https://signed-input-url",
  "image_urls": ["https://signed-input-url"],
  "parameters": {"resolution": "4K", "outputFormat": "PNG"},
  "response_format": "url"
}
```

It accepts a JSON response containing one of:

- `image_url`, `imageUrl`, `url`, `output.url`, or `data[0].url`
- `base64`, `image_base64`, `b64_json`, or a `data:image/...;base64,...` value

The backend validates the response, archives the bytes into COS, writes `agent_asset`, and emits an `image` event. Provider credentials never reach the browser.

## Frontend flow

1. User selects an image on `/zh/tool/{slug}`.
2. Browser uploads it to `/api/v1/agent/media/images?sessionId=...` with the JWT.
3. Browser sends the returned `assetId` and form parameters to the workflow SSE endpoint.
4. Browser parses `data: {AgentEvent}` frames and renders the event list.
5. The final `image` event contains the signed COS URL and asset ID.

## Verification

Backend compile:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -DskipTests compile
```

Frontend build:

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm build
```

## Next optimization

- Add vendor-specific request mappers for each chosen provider instead of relying on the normalized JSON contract.
- Add async job polling/webhook support for providers that return a task ID instead of an image in the initial response.
- Add per-tool cost/timeout limits and a workspace asset detail panel for workflow runs.