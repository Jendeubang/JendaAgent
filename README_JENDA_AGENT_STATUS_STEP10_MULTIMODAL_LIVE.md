# Step 10 Multimodal Live Agent

## Delivered

`/live-multimodal` is the end-to-end demo route for a real multimodal agent run:

1. A user selects an image in the live agent page.
2. The page requests a restricted STS ticket and uploads directly to COS.
3. Backend completion verification returns a signed HTTPS `imageUrl`.
4. The page sends that URL in `imageUrls` to the existing agent run API.
5. `ModelToolAgentRunService` passes every URL to PlanningAgent and SummaryAgent
   through OpenAI-compatible `image_url` content and makes OCR eligible in the
   ExecutorAgent tool selection.
6. The page renders persisted SSE events and retains the reference asset in its
   Workspace panel.

## Existing Persistence

`AgentHistoryStore` already stores `image_urls_json` for every agent run and
stores the `RUN_STARTED` event payload before SSE delivery. This makes the
multimodal input URL replayable with the rest of the agent timeline.

## Workspace Limitation

The current desktop sandbox did not permit changing existing source files, so
the implementation is delivered as the new route rather than replacing
`/live`. The existing backend request model stores image URLs but does not yet
store the browser asset ID. The next source-edit pass should add `asset_ids_json`
to `agent_run`, carry `assetIds` in `AgentRunRequest`, and fold this route into
`/live`.

## Validation

Start the backend with COS environment variables, start Next.js with
`NEXT_PUBLIC_AGENT_API_BASE_URL=http://127.0.0.1:19090`, then open:

`http://localhost:3000/live-multimodal`

Expected sequence: STS ticket, COS PUT, upload completion, `run_started`,
`plan`, optional OCR task/tool result, `summary`, and `run_completed`.
