# Step 51: Image-generation runtime wiring

## Root cause

The Qwen image-generation gateway existed and had a configured Model Studio endpoint, but the deployed `ExecutorAgent` had no `IMAGE_GENERATE` internal endpoint mapping. It therefore skipped the tool as unconfigured.

## Implementation

- Map `AGENT_RUNTIME_TOOLS_IMAGE_GENERATE_*` to the in-container `/internal/agent-tools/qwen-image-generate` gateway.
- Generate and store a dedicated internal gateway authorization secret in uncommitted `deploy/.env`.
- Align the Qwen image-generation HTTP connect/read/write timeout with the 600-second image workflow timeout.

## Verification

Submit an image generation request at `/zh/agent`. The event stream should report `ToolRouter: image generation completed`; a generated result should then be archived to COS.