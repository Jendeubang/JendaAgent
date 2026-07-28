# Jenda Agent Current Status Addendum

Read this file after `README_JENDA_AGENT.md`. It records changes made after the initial architecture guide.

## Completed In This Change Set

### Real SSE Frontend

The real integration page is now available at:

```text
http://localhost:3000/live
```

Source files:

```text
showcase/app/live/page.tsx
showcase/app/live/page.module.css
showcase/.env.example
```

It uses native `fetch` plus `ReadableStream` to consume the POST SSE endpoint. Browser `EventSource` is not used because it cannot send the required POST request body.

The page:

- Sends `prompt`, `mode` and optional `imageUrls`.
- Parses `event: agent-event` frames.
- Maps `plan`, `task`, `tool_result`, `image` and `summary` to typed UI cards.
- Writes image events to the Redux Workspace asset state.
- Calls the replay endpoint to rebuild a session timeline.

Set the backend target before starting the frontend when port 8080 is unavailable:

```text
NEXT_PUBLIC_AGENT_API_BASE_URL=http://127.0.0.1:18080
```

Copy `showcase/.env.example` to `showcase/.env.local`, change the URL, and restart `corepack pnpm dev`.

### History Persistence

`PersistentAgentRunService` is annotated with `@Primary`, so Spring injects it instead of `DemoAgentRunService` for `AgentRunController`.

Its critical order is:

```text
create session/run
-> build AgentEvent
-> persist AgentEvent and typed child payload
-> send agent-event SSE frame
-> mark run complete or failed
```

New sources:

```text
genie-backend/src/main/java/com/jd/genie/service/agent/AgentHistoryStore.java
genie-backend/src/main/java/com/jd/genie/service/agent/PersistentAgentRunService.java
genie-backend/src/main/java/com/jd/genie/controller/AgentHistoryController.java
```

Tables are created at application startup with portable `CREATE TABLE IF NOT EXISTS` statements:

```text
agent_session
agent_run
agent_message
agent_plan_message
agent_task_message
agent_tool_result_message
agent_image_message
agent_summary_message
```

The common `agent_message` table preserves original event order. The five child tables hold type-specific data for history replay and future detailed UI rendering.

### New Replay API

```http
GET /api/v1/agent/sessions/{sessionId}/events
```

It returns ordered `AgentEvent` envelopes. The run API is still:

```http
POST /api/v1/agent/sessions/{sessionId}/runs
Accept: text/event-stream
```

## Validation

Passed:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dmaven.repo.local=F:\JendaAgent\.m2" -DskipTests compile

cd F:\JendaAgent\JendaAgent\showcase
node_modules\.bin\tsc.cmd --noEmit --skipLibCheck
node_modules\.bin\next.cmd build --no-lint --experimental-build-mode compile
```

## Remaining Work, In Order

1. Restart the backend with the new source on a free port and use `/live` for end-to-end SSE validation.
2. Replace the fixed event payloads in `PersistentAgentRunService` with PlanningAgent, ExecutorAgent and SummaryAgent adapters.
3. Promote `/live` to the root showcase page after acceptance testing.
4. Replace startup DDL with versioned MySQL migrations before production use.
5. Add COS upload, validate `image_url`, then integrate Qwen, OCR, image generation/editing and PromptOpAgent.

## Documentation Note

The intended architecture README is `README_JENDA_AGENT.md`. A Windows sandbox issue prevented an in-place update during this change set, so this addendum is the authoritative continuation until the files can be merged into one README.
## Native Toolbox Providers (Step 73)

The toolbox now has tool-compatible model preferences. `image-upscale` and `seedvr2` route to `VolcengineSeedVr2ToolProvider`; `image-layered` routes to `SemanticLayerImageToolProvider`. Native providers receive COS-signed source URLs, run server-side, return normalized output, archive every layer to COS, and emit the same persisted SSE image events used by other workflows.

Configuration and provider response requirements are documented in `README_JENDA_AGENT_STATUS_STEP73_NATIVE_TOOL_PROVIDERS.md`. Keep all credentials in `deploy/.env` only.
## Agent Delivery Panel Default

The /zh/agent delivery panel now defaults to collapsed, including immediately after generated assets arrive. Users expand it explicitly through its section toggle; generated and reference assets remain persisted and reusable.
## Tool Capability Registry and True Replanning (Step 74)

`AgentToolCapabilityRegistry` now gives PlanningAgent a runtime-derived, credential-free list of selectable tools. The same snapshot is emitted through SSE and can be read from authenticated `GET /api/v1/agent/tool-capabilities`.

Structured Plan-Solve now persists immutable plan revisions in `agent_plan_revision`. Terminal failed tasks or unavailable tool skips can trigger a bounded PlanningAgent replan. Replacement tasks are prefixed by plan revision, preserving previous task history for audit and replay. See `README_JENDA_AGENT_STATUS_STEP74_CAPABILITY_REPLAN.md` for configuration and persistence details.
## Shared Task Memory and Controlled ReAct (Step 75)

Plan-Solve now persists each DAG subtask's input, output, asset IDs, state and failure cause in `agent_shared_task_memory`. `MemoryMergeAgent` produces a deterministic traceable context and `SummaryAgent` uses that merged context only. ReAct stays sequential by default but supports a validated two-call `PARALLEL` action for independent tools, with call/cost budgets, stable idempotency fingerprints, cancellation and an automatic client-side handoff to Plan-Solve when the model selects `PLAN_SOLVE`. See `README_JENDA_AGENT_STATUS_STEP75_SHARED_MEMORY_CONTROLLED_REACT.md`.
