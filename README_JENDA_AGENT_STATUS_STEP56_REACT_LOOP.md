# Step 56: Bounded ReAct Runtime

## Goal

React mode is now a real bounded agent loop rather than a single planning and tool-call pass:

```text
Think -> Act -> Observation -> Next Action
```

`/zh/agent` defaults to React mode while Planning mode remains off. Turning Planning mode on continues to use the independent structured Plan-Solve DAG runtime from Step 55.

## Per-Round Protocol

`ReActDecisionGenerator` asks the configured model for one JSON decision only:

```json
{
  "reasoning": "brief operational reasoning summary",
  "action": "OCR | IMAGE_GENERATE | IMAGE_EDIT | FINISH",
  "toolInput": "normalized input for the selected tool",
  "nextDecision": "what to evaluate after the observation"
}
```

The runtime persists and sends these events for every round:

- `react_think`: reasoning summary and prior observations.
- `react_decision`: selected next action, normalized tool input, and next-decision text.
- `react_act`: the tool action and attempt number.
- `react_observation`: tool input, result, provider, success state, output image URL, and next-decision text.
- `react_terminated`: bounded termination caused by timeout, maximum steps, or repeated-call protection.

Image outputs continue through the existing `image` event and are registered in the COS/MySQL asset workflow.

## Persistence

Base event replay remains in `agent_message`. A dedicated `agent_react_message` child table records round number, phase, reasoning summary, tool input, tool result, next decision, and display content. This makes React timelines replayable after a backend restart.

## Safety Limits

Configuration is environment-driven:

```powershell
$env:AGENT_REACT_MAX_STEPS="6"
$env:AGENT_REACT_TIMEOUT="PT8M"
$env:AGENT_REACT_REPEATED_TOOL_LIMIT="2"
```

- `max-steps`: clamped to `1..12`.
- `timeout`: total execution deadline; default `PT8M`.
- `repeated-tool-limit`: identical `action + normalized tool input + image URLs` guard; clamped to `1..5`.

When the planner model is unavailable or returns invalid JSON, the runtime chooses one safe compatible fallback action. It never performs OCR or image editing without an input image.

## Frontend

`/zh/agent` now extracts `react_*` events from the generic message list and renders a collapsible **ReAct Timeline**. Each item is labeled with its round and shows the reasoning summary, selected action, tool input, observation, and next decision. The normal start, image asset, summary, and error cards remain visible outside the timeline.

## Verification

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -DskipTests compile

cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm build
```

Both commands passed for this milestone. Existing upstream compiler warnings and the unrelated `app/upload/page.module.css` Autoprefixer warning remain.

## Follow-up Work

- Add provider-side cancellation when the global timeout is reached during an in-flight request.
- Add test coverage for timeout, duplicate fingerprint, `FINISH`, and multi-round image results.
- Allow a user to choose an explicit maximum step budget in advanced agent settings.
