# Step 47: Qwen image-edit inference timeout alignment

## Root cause

The image-edit property configured a 600-second call timeout, but OkHttp kept its default 10-second read timeout. Qwen image-edit inference therefore failed while waiting for response headers, despite the longer configured limit.

## Implementation

`QwenImageEditGatewayClient` now sets:

- connect timeout: 30 seconds
- read timeout: configured image-edit timeout (default 600 seconds)
- write timeout: configured image-edit timeout
- total call timeout: configured timeout plus 30 seconds
- HTTP/1.1 protocol to avoid observed HTTP/2 stream timeout behavior

## Verification

Run image edit with a private COS reference image. A model request may take tens of seconds and must remain active until Qwen returns a completed response.