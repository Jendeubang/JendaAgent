# Step 46: Private COS URLs for image-edit models

## Root cause

Qwen Image Edit received a private COS object URL without a query signature. Model Studio attempted to download it and COS correctly returned `403 Forbidden`.

## Implementation

- `QwenImageEditGatewayClient` detects the optional COS signer at runtime.
- Input URLs belonging to Jenda's configured COS bucket are transformed into short-lived signed `GET` URLs before the Qwen request is assembled.
- External URLs are not changed.
- The COS bucket remains private; no public-read policy is required.

## Verification

Run an image-edit request using an uploaded reference image. The model gateway must no longer report `Failed to download image ... 403`.