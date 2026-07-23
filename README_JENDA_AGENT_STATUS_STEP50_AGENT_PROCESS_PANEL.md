# Step 50: Sequential agent process panel

## UI change

The `/zh/agent` runtime display no longer renders every SSE event as an independent chat bubble. Each run is rendered inside one `Agent Workflow` card with an ordered process rail.

## Content preserved

- event ordering from SSE remains unchanged
- every event still exposes its stage, agent, status, title and content
- active work adds a trailing pending step
- generated and reference assets remain visible beneath the process sequence

## Stages

`run_started`, `plan`, `task`, `tool_call`, `tool_result`, `image` and `summary` are mapped to readable Chinese stage labels.

## Verification

Run a text-to-image or image-edit request at `/zh/agent`; process events should appear inside a single numbered task flow card rather than a long set of bubbles.