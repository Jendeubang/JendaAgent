# Step 45: COS upload transport hardening

## Problem

Docker-hosted backend uploads could time out while Tencent COS closed the underlying upload stream. The browser then surfaced only the generic `Server upload fallback failed: 500` message.

## Changes

- `CosAgentImageStorage` forces OkHttp to use HTTP/1.1 and adds `Connection: close` for the COS `PUT` request, avoiding unstable HTTP/2 stream reuse observed through Docker Desktop networking.
- The frontend now reports both the COS direct-upload failure and server-upload fallback failure in a single error message.

## Verification

1. Rebuild `backend` and `showcase` images.
2. Upload an image at `/zh/agent`.
3. If it still fails, use the displayed two-part message to distinguish COS CORS/direct failures from backend COS transport failures.

## Operational requirement

COS CORS must allow both `http://localhost` and `http://localhost:3000` during local development, with `PUT`, `GET`, and `HEAD` methods.