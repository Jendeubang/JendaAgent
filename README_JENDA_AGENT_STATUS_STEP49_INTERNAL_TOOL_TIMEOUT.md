# Step 49: Internal image-edit tool timeout propagation

## Root cause

The internal `HttpAgentToolClient` used by `ExecutorAgent` only configured OkHttp `callTimeout`. OkHttp's default 10-second read timeout still ended the self-call to `/internal/agent-tools/qwen-image-edit` before the gateway could wait for image inference.

## Implementation

The internal tool client now aligns all transport timeouts with each endpoint's configured timeout:

- connect: 30 seconds
- read/write: endpoint timeout (`IMAGE_EDIT` defaults to 600 seconds in deployment)
- total call: endpoint timeout plus 30 seconds

## Result

The dynamic agent's `IMAGE_EDIT` tool call can remain open while the gateway waits for Qwen image-edit completion. SSE heartbeats continue independently.