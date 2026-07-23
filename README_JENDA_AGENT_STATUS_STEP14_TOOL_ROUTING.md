# Step 14: Tool Routing and Tool Call Events

## Intended Changes

- Add `tool_call` to the unified SSE event protocol.
- Persist each tool invocation in `agent_tool_call_message` and retain the common ordered envelope in `agent_message`.
- Emit `task -> tool_call -> tool_result` before and after concurrent tool execution.
- Require explicit image-generation language such as `生成一张` or `文生图`; an OCR prompt that merely mentions an image must not start image generation.
- Render `tool_call` as `工具调用` in `/live-multimodal`.

## Apply And Verify

Apply `patches/step14-tool-routing-and-tool-call.patch` from the repository root, rebuild the backend, then submit `识别图片中的全部文字，并保留换行。` with one uploaded image.

Expected order: `run_started`, `plan`, `task(OCR)`, `tool_call(OCR)`, `tool_result(OCR)`, `summary`. No image-generation task should appear.

## Next Improvements

- Define a structured input/output schema for each tool.
- Persist tool duration, retry count, and provider request identifier.
- Add a Workspace timeline that groups task, call, and result.
