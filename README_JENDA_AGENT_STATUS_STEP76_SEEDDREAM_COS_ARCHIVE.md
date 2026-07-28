# Step 76 - SeedDream / Volcengine COS Archive

## Objective

Make Volcano Ark SeedDream result images durable, private COS assets instead of exposing temporary provider URLs.

## Implementation

- `GeneratedImageCosArchiver` still accepts HTTPS only and only downloads from explicit host suffixes.
- Built-in allowlist keeps Model Studio suffixes and adds Volcano Ark/TOS result suffixes:
  - `.volces.com`
  - `.volcengine.com`
  - `.byteimg.com`
- The configured list is exposed through `AGENT_GATEWAY_SEEDDREAM_RESULT_HOST_SUFFIXES` for future provider changes.
- COS upload now uses the platform DNS resolver and a 90-second call timeout. This avoids forcing uploads onto unstable COS edge IPs in Docker Desktop.
- Generated assets remain under the configured COS prefix and are returned through the existing signed URL path.

## Configuration

```env
AGENT_IMAGE_PROVIDER_DEFAULT=seedream
AGENT_GATEWAY_SEEDDREAM_ENABLED=true
AGENT_GATEWAY_SEEDDREAM_ENDPOINT=https://ark.cn-beijing.volces.com/api/v3/images/generations
AGENT_GATEWAY_SEEDDREAM_MODEL=<your Ark endpoint ID>
AGENT_GATEWAY_SEEDDREAM_API_KEY=<your Ark API key>
AGENT_GATEWAY_SEEDDREAM_RESULT_HOST_SUFFIXES=.volces.com,.volcengine.com,.byteimg.com
```

Do not use a broad wildcard or arbitrary URL host list. Provider output is downloaded server-side, so the suffix allowlist is an SSRF boundary.

## Verification on 2026-07-28

- Container to COS HTTPS probe completed successfully (private bucket root returned expected HTTP 403).
- Real SeedDream text-to-image request returned provider `seedream-cos`, a COS result host, and `archivedToCos=true`.
- Real SeedDream image-edit request using a private COS reference image returned provider `seedream-image-edit-cos`, a COS result host, and `archivedToCos=true`.
- No API key, temporary provider URL, or COS signed URL is stored in this document.

## Follow-up

- Keep generated/edit outputs linked to `agent_asset` when the request runs through the normal agent workflow. The internal adapter only verifies provider and COS transport; workflow persistence remains the responsibility of the orchestration path.
- If Volcengine introduces a new result domain, add only its documented suffix to `AGENT_GATEWAY_SEEDDREAM_RESULT_HOST_SUFFIXES`, then rebuild and rerun this verification.