# Step 43: COS Upload Resilience and CORS

## Backend Resilience

`CosAgentImageStorage` now uses explicit OkHttp connect/read/write/call timeouts and retries transient COS network and 5xx failures up to three times. Non-retryable 4xx responses still fail immediately so configuration errors remain visible.

## Required COS CORS Origins

The deployed Docker site runs at `http://localhost`, not `http://localhost:3000`. In Tencent COS console, edit the bucket CORS rule and include both origins:

```text
http://localhost
http://localhost:3000
```

Set methods to `PUT, GET, HEAD`, allow headers `*`, and expose `ETag`. The browser needs `PUT` CORS permission for STS direct upload.

## Upload Flow

1. Browser asks backend for STS credentials.
2. Browser uploads directly to COS.
3. Backend verifies the object and records the asset metadata.
4. If direct upload fails, backend uploads the file to COS with three retry attempts.

## Diagnostics

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml logs --tail 120 backend
```