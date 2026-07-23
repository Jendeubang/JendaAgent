# Step 15: Session History Replay UI

## Delivered

- Added `/history-replay`, a standalone history timeline UI that calls `GET /api/v1/agent/sessions/{sessionId}/events`.
- Defaults to `showcase-live-multimodal-session`, the same session used by `/live-multimodal`.
- Replays stored events in the backend order and excludes heartbeat events.
- Shows `plan`, `task`, `tool_call`, `tool_result`, `image`, `summary`, and lifecycle events with individual timeline styling.
- Supports custom session IDs, refresh, empty state, loading state, and request error state.

## Usage

1. Run a task in `/live-multimodal`.
2. Open `http://localhost:3000/history-replay`.
3. Click `回放历史`.
4. The action only queries MySQL history; it does not invoke the model or tools.

## Architecture

```text
MySQL agent_message + typed message tables
  -> AgentHistoryStore.replay(sessionId)
  -> GET /api/v1/agent/sessions/{sessionId}/events
  -> /history-replay timeline
```

## Next Improvements

- Add a session list and run-level filtering.
- Group events into collapsible run cards.
- Restore uploaded references and generated images into Workspace.
