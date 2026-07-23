# Step 26: COS Upload Fallback

## Problem

Browser STS direct upload can fail before returning an HTTP status when a local proxy, TLS interception, or COS CORS preflight blocks the signed PUT request. The UI otherwise reports only `Failed to fetch`.

## Behavior

1. The browser first requests a scoped STS ticket and uploads directly to COS.
2. If any direct-upload step fails, the frontend submits the same file to `/api/v1/agent/media/images`.
3. The backend uses the existing server-side COS uploader and returns the same model-accessible signed URL contract.

No permanent COS credential is exposed to the browser in either path.

## Recommended COS CORS Rule

For direct upload to work in local development, allow both origins:

- `http://localhost:3000`
- `http://127.0.0.1:3000`

Allow methods `PUT`, `GET`, `HEAD`; allow headers `*`; expose `ETag`; use a short max-age such as `600` seconds.
