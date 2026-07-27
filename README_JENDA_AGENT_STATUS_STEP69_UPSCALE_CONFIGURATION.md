# Step 69: Image Upscale and SeedVR2 Configuration

## Fixed in This Step

- Tool-page sessions are scoped by authenticated user ID. A session created before switching accounts can no longer cause a `403` when a tool run starts.
- The tool workbench displays `工具服务返回 HTTP <status>` rather than mojibake.

## Required Configuration

Image upscale and SeedVR2 need their own upstream processing provider. Qwen image generation/editing does not become a true super-resolution service merely by changing the prompt.

Copy these entries from `deploy/.env.example` into `deploy/.env` and fill them with the values from the selected provider:

```dotenv
AGENT_TOOLS_IMAGE_UPSCALE_ENABLED=true
AGENT_TOOLS_IMAGE_UPSCALE_ENDPOINT=https://your-provider.example/v1/image/upscale
AGENT_TOOLS_IMAGE_UPSCALE_API_KEY=your-provider-api-key
AGENT_TOOLS_IMAGE_UPSCALE_MODEL=your-upscale-model-id
AGENT_TOOLS_IMAGE_UPSCALE_MODE=edit
AGENT_TOOLS_IMAGE_UPSCALE_TIMEOUT=600s

AGENT_TOOLS_SEEDVR2_ENABLED=true
AGENT_TOOLS_SEEDVR2_ENDPOINT=https://your-provider.example/v1/seedvr2
AGENT_TOOLS_SEEDVR2_API_KEY=your-provider-api-key
AGENT_TOOLS_SEEDVR2_MODEL=your-seedvr2-model-id
AGENT_TOOLS_SEEDVR2_MODE=edit
AGENT_TOOLS_SEEDVR2_TIMEOUT=600s
```

## Provider Contract

The configured endpoint receives JSON containing `model`, `prompt`, `image_url`, `image_urls`, `parameters`, and `response_format: "url"`. It must return one of:

- `image_url`, `imageUrl`, `url`, `data[0].url`, or `output.url`
- `base64`, `image_base64`, or `b64_json`

The backend archives the returned result in COS and records it in the asset center.

## Apply

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```

For now, fix the 403 by hard-refreshing the tool page and uploading the file again. If the next error says the provider is not configured, fill the corresponding variables above.