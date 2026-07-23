# Step 55: Structured Plan-Solve DAG

## Goal

Replace rule-first Plan-Solve tool routing with a durable, model-produced execution plan. The runtime now validates a structured JSON plan, executes its dependency graph with observable state changes, and pauses safely for human approval.

## Runtime Flow

```text
Browser (plan mode)
  -> POST /api/v1/agent/sessions/{sessionId}/runs
  -> PlanningAgent / StructuredPlanGenerator
  -> JSON Schema + DAG validation
  -> agent_plan_execution + agent_plan_task_state
  -> ObservableDagTaskExecutor
       -> dependency checks / parallel groups / retries / skip conditions
       -> tool_call, tool_result, image SSE events
       -> agent_asset metadata + COS image URL
  -> SummaryAgent
```

If a plan contains `HUMAN_CONFIRMATION` or a task with `requiresConfirmation=true`, the executor writes an approval row, emits `confirmation_required`, and completes only the current SSE response. The run remains resumable.

```text
POST /api/v1/agent/sessions/{sessionId}/runs/{runId}/approvals/{approvalId}
{ "approved": true, "note": "optional operator note" }
```

The agent page renders `Confirm and continue` and `Reject` actions for this event. The continuation loads the saved plan and task states, so completed nodes are not executed again.

## Structured Plan Contract

The source JSON Schema is at `genie-backend/src/main/resources/plan-solve/agent-plan.schema.json`.

Each task has:

- `id`: stable lowercase DAG identifier.
- `kind`: `OCR`, `IMAGE_GENERATE`, `IMAGE_EDIT`, or `HUMAN_CONFIRMATION`.
- `dependsOn`: predecessor task IDs.
- `parallelGroup`: observable concurrency grouping label.
- `maxAttempts`: bounded retry count from 1 to 3.
- `skipWhen`: `never`, `no_input_image`, `previous_failed`, or `tool_unavailable`.
- `requiresConfirmation` and `confirmationMessage`: human-in-the-loop gate.

`PlanJsonSchemaValidator` enforces the declared schema constraints plus unique task IDs, known dependencies, and cycle-free DAG dependencies. Invalid model JSON never reaches the executor. The runtime uses a safe deterministic plan only if the configured planner is unavailable or returns invalid JSON.

## Persistence

New MySQL/H2-compatible tables are created automatically:

- `agent_plan_execution`: input request, validated plan, owner, and run status.
- `agent_plan_task_state`: each DAG node's state, attempt count, and latest result.
- `agent_plan_approval`: pending/approved/rejected operator decisions.

Existing `agent_message` continues to store replayable SSE events. New event/status values are `confirmation_required`, `waiting_confirmation`, and `skipped`.

## Concurrency

`ObservableDagTaskExecutor` uses a fixed-size `ExecutorCompletionService` worker pool instead of the prior direct `CompletableFuture` fan-out. Before every execution, retry, failure, skip, pause, and completion transition it persists task state and emits an SSE event. This keeps execution trace, history replay, and workspace assets aligned.

## How To Use

1. Start backend and frontend with the existing model/tool environment variables.
2. Open `/zh/agent` and switch `Planning mode` to enabled.
3. Submit a normal task. The PlanningAgent event contains the persisted structured DAG.
4. Ask for confirmation in the task, for example: `Confirm the prompt before generating a product poster`.
5. Click `Confirm and continue` or `Reject` when the confirmation card appears.

## Verification

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -DskipTests compile

cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm build
```

Both commands passed for this milestone. The current repository still has pre-existing compiler and Autoprefixer warnings unrelated to this step.

## Follow-up Work

- Add a production JSON Schema engine if external schema composition is needed.
- Make `MAX_CONCURRENCY` environment-configurable and expose per-task queue metrics.
- Add explicit compensation nodes for workflows that mutate external systems.
- Add API tests for approval authorization, restart/resume, retry exhaustion, and dependency-cycle rejection.
