# Step 28: Authenticated User Experience and Asset Ownership

## Delivered

- Adds an account dropdown to Agent Studio and Image Workbench.
- Logout clears JWT plus browser-local session IDs before returning to `/login`.
- `agentFetch` detects API `401`, clears browser identity state, and redirects to `/login?reason=expired`.
- Adds `agent_asset` metadata with `asset_id`, `owner_user_id`, `session_id`, `run_id`, COS object key, signed image URL, source, and timestamp.
- Stores direct COS uploads after verification with their authenticated session owner.
- Stores server-upload fallback images with the same metadata.
- Stores generated/image-edit outputs when the dynamic Plan-Solve runtime emits the `image` event.
- Adds authenticated asset inspector endpoint: `GET /api/v1/agent/assets/{assetId}`.

## Data Boundary

COS remains private and uses short-lived signed URLs. The database is the authority for ownership:

```text
agent_user -> agent_session -> agent_run -> agent_message
                    |
                    +-> agent_asset (owner_user_id, session_id, run_id)
```

An asset inspector request returns `404` when the requested asset does not belong to the JWT user.

## Verification

1. Sign in, upload a reference image, and run image generation or editing.
2. Inspect stored metadata:

```powershell
mysql -uroot -p123456 -D jenda_agent -e "SELECT asset_id, owner_user_id, session_id, run_id, source, file_name, created_at FROM agent_asset ORDER BY created_at DESC;"
```

3. Use the account button in the header and choose `退出登录`.
4. Confirm `/login` opens and a new sign-in creates a new client session ID.
5. Let a token expire or replace it with an invalid value in browser local storage; the next agent API request must redirect to login.

## Follow-up Work

- Add refresh token rotation and server-side token revocation.
- Associate COS lifecycle cleanup with user/session deletion.
- Add an asset list API with pagination and retention controls.
