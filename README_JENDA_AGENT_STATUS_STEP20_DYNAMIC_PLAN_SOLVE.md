# Step 20: Dynamic Plan-Solve Loop

## Delivered

- Added `POST /api/v2/agent/sessions/{sessionId}/runs`, isolated from the stable v1 runtime.
- Added `DynamicPlanSolveAgentRunService` with a maximum of three planning rounds.
- Each round emits structured `plan.steps`, `task`, `tool_call`, `tool_result`, and optional `image` events.
- Tool calls within a round run concurrently.
- Shared memory records successful observations, unavailable tools, and failure reasons.
- Only invoked failures are selected for the next round. Not-configured tools are never retried blindly.
- Final summary includes the shared-memory delivery context.

## Dynamic Rule

```text
plan -> concurrent solve -> observe results
  -> all complete: summary
  -> invoked failure and rounds remain: replan with shared memory
  -> unavailable tool or round limit: summarize available delivery
```

## Next Work

- Replace rule-derived tool selection with a model-generated structured plan validated by JSON Schema.
- Add dependency-aware task graph scheduling instead of only tool-level parallelism.
- Add a first-class round timeline group to Workspace.
