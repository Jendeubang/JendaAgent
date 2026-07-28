# Step 74: Tool Capability Registry and Versioned Replanning

## What Changed

Plan-Solve no longer hard-codes `OCR`, `IMAGE_GENERATE`, and `IMAGE_EDIT` into the PlanningAgent prompt.

`AgentToolCapabilityRegistry` resolves the currently configured runtime endpoints and publishes a credential-free capability snapshot. It is injected into:

- the initial `run_started` and `plan` SSE payloads;
- the PlanningAgent JSON prompt;
- deterministic fallback planning;
- authenticated discovery API: `GET /api/v1/agent/tool-capabilities`.

Only endpoint names, availability and capability tags are exposed. URLs, API keys and provider credentials are never returned to the browser.

## Real Replan Loop

When a structured DAG reaches terminal state, `DynamicPlanSolveAgentRunService` now:

1. collects failed tasks and unavailable-tool skips from the durable task-state table;
2. checks the bounded replan policy;
3. calls `PlanningAgent` again with the previous plan, terminal observations and the current capability snapshot;
4. validates the returned replacement JSON DAG;
5. prefixes replacement task IDs (`r2-*`, `r3-*`) so historical task records remain immutable;
6. persists the replacement plan and its initial task states in one transaction;
7. emits a new `plan` SSE event with `replan=true`, revision metadata and the trigger reason;
8. executes the new DAG, or enters SummaryAgent only after the loop is exhausted or no replan is needed.

The default is an initial plan plus two replacement plans. Configure in `deploy/.env`:

```env
AGENT_PLAN_SOLVE_MAX_REPLANS=2
AGENT_PLAN_SOLVE_REPLAN_ON_FAILED_TASK=true
AGENT_PLAN_SOLVE_REPLAN_ON_SKIPPED_TOOL=true
```

## Persistence

Flyway migration `V3__plan_revisions.sql` adds:

```text
agent_plan_revision(run_id, revision_no, parent_revision_no, reason,
                    plan_json, state_summary_json, created_at)
```

`agent_plan_execution.plan_json` points to the active version. Prior versions and their `agent_plan_task_state` records are preserved for replay and audit.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -q -DskipTests compile
```

## Next Work

- Add a plan revision viewer in the history workspace.
- Add an explicit planner-level tool cost/risk policy.
- Add focused integration tests that force one provider failure then assert an `r2-*` revision is stored.