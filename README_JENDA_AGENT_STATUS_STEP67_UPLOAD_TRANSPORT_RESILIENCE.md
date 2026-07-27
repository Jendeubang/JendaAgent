# Step 67: Upload Transport Resilience

## Scope

- Browser uploads use COS STS direct upload by default, then fall back to the authenticated backend route only if direct upload fails.
- Set `NEXT_PUBLIC_AGENT_COS_DIRECT_UPLOAD=false` only to force backend-only uploading for diagnosis.
- COS server uploads use HTTP/1.1, bounded requests, five retries, exponential backoff, and rotating DNS answer order.
- The COS bucket CORS policy permits local origins on ports `3000`, `8088`, and default port `80`, with `PUT`, `Content-Type`, `Authorization`, and `x-cos-security-token` allowed.

## Why

The deployed JendaAgent page runs through Nginx at port `8088`, while the previous bucket CORS policy allowed only port `3000`. COS therefore rejected the browser preflight before the PUT request. Direct STS upload is now usable from the deployed page, avoiding Docker-to-COS transient upload timeouts.

## Runtime Behavior

1. The frontend requests a scoped STS ticket and uploads the image directly to COS from the browser.
2. The frontend verifies the upload with the backend, which creates a signed read URL and records asset metadata.
3. When direct upload cannot run, the frontend falls back to `/api/v1/agent/media/images`.
4. The UI receives an `imageUrl` suitable for preview and downstream image tools.

## Verification

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml ps
```

Sign in at `http://localhost:8088/zh/agent`, upload a PNG or JPG under 10 MB, then submit an OCR or image-edit task. The image should be previewed and a new `agent_asset` record should be created.

## Operational Note

If direct upload fails after a CORS policy change, hard-refresh the browser to discard an old preflight cache and confirm the page origin is one of the configured origins. The backend fallback remains available for isolated diagnostics.