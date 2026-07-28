# Step 75: Shared Task Memory and Controlled ReAct

## Scope

This phase makes Plan-Solve task output traceable before it reaches `SummaryAgent`, and extends ReAct without turning every step into unsafe parallel execution.

## Plan-Solve Shared Memory

`agent_shared_task_memory` is introduced by Flyway migration `V4__shared_task_memory.sql`.

Each DAG task persists one current memory record keyed by `(run_id, task_id)`:

- tool name, state, retry attempt
- normalized task input and output JSON
- COS/generated asset IDs
- failure reason
- delivery-safe task summary

`ObservableDagTaskExecutor` writes the memory record at start, completion, failure and skip. `MemoryMergeAgent` deterministically merges only these persisted records. `SummaryAgent` receives `MergedTaskContext.summaryInput()` rather than raw scheduler state, so it has task IDs, asset IDs, completed facts and failures but no invented cross-task result.

## Controlled ReAct

Default ReAct remains sequential: `Think -> Act -> Observation -> Next decision`.

The model may return `PARALLEL` only for exactly two independent calls. Validation rejects duplicate tools, dependent generation-plus-edit pairs, and OCR/edit calls without an input image. Parallel calls are emitted with the same tool-call/result lineage as sequential calls and are collected before the next decision.

Controls:

- `AGENT_REACT_MAX_PARALLEL_ACTIONS=2`
- `AGENT_REACT_MAX_TOOL_CALLS=8`
- `AGENT_REACT_MAX_COST_UNITS=12` (`OCR=1`, image generation/editing=`3`)
- `AGENT_REACT_REPEATED_TOOL_LIMIT=2` via stable fingerprint including tool, normalized input, reference images and selected provider
- `POST /api/v1/agent/sessions/{sessionId}/runs/{runId}/cancel` cooperatively cancels an active ReAct run for its owner

A model can emit `PLAN_SOLVE` when its observation indicates that sequential ReAct should be replaced by a dependency-aware replan. The ReAct SSE stream emits a handoff event; `/zh/agent` automatically continues the same prompt and reference images as a new Plan-Solve run.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -q -DskipTests compile
mvn -q -Dtest=MemoryMergeAgentTest test
```

After packaging and restarting the backend, run a Plan-Solve request with multiple independent tasks and confirm the SSE stream includes `MemoryMergeAgent` before `SummaryAgent`. For ReAct, inspect `react_act` and `react_observation`: `parallel=true` appears only for an accepted `PARALLEL` batch.