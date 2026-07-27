# Step 64: COS Upload Reliability

## Problem

The browser tried STS direct upload first. When browser-to-COS CORS or network transport failed, it fell back to `POST /api/v1/agent/media/images`. The fallback was returning HTTP 500 after COS TLS negotiation was closed by the remote peer.

## Implementation

- `CosAgentImageStorage` forces HTTP/1.1 for COS traffic. This avoids intermittent HTTP/2 TLS negotiation failures observed from Docker Desktop.
- The upload controller replacement now creates `SignedCosAgentMediaController` directly when COS is enabled.
- Fallback uploads now consistently:
  - persist `agent_asset` ownership/session metadata;
  - return a signed HTTPS COS URL;
  - remain accessible to model adapters and the workspace;
  - support normal asset deletion and COS object cleanup.

## Browser direct upload

The frontend still attempts STS direct upload first. It automatically uses the verified backend fallback if the browser cannot reach COS because of CORS, VPN, or network policy.

For direct browser upload, COS CORS should allow the deployed origins, `PUT`, and these request headers:

```text
content-type
authorization
x-cos-security-token
```

Typical local origins are `http://localhost`, `http://localhost:3000`, and `http://127.0.0.1:3000`.

## Verification

On 2026-07-27, a 1x1 PNG was uploaded through `http://127.0.0.1:8088/api/v1/agent/media/images` with a valid JWT. The endpoint returned HTTP 200, `storageProvider=cos`, `modelAccessible=true`, and a signed URL. The created asset was then deleted through the asset API with HTTP 200, which also cleaned up the COS object.