# Step 29 Addendum: V1 Generated Asset Persistence

`ModelToolAgentRunService` now records every successful image tool result in `agent_asset` before publishing the `image` SSE event.

Each generated asset row carries:

- `owner_user_id` from the claimed agent session.
- `session_id` and `run_id` from the active SSE run.
- `source = generated`.
- The signed COS output URL used by Workspace and image preview.
