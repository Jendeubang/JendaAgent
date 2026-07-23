# Step 27: MySQL Persistence and JWT User Isolation

## Delivered

- Adds `agent_user` with PBKDF2-HMAC-SHA256 password hashes.
- Adds signed HS256 JWT registration/login endpoints:
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
- Protects only JendaAgent APIs under `/api/v1/agent/**` and `/api/v2/agent/**`; upstream JoyAgent endpoints are unchanged.
- Adds `owner_user_id` to `agent_session` and `agent_run` plus `agent_session_claim` for an upload-first session.
- Applies owner filtering to SSE run creation, history replay, session list and Workspace asset queries.
- Binds COS STS tickets to the authenticated user that claimed the session.
- Adds `/login` in the Next.js showcase and an authenticated API fetch helper.

## Persistence Modes

Development fallback remains file H2:

```powershell
$env:AGENT_HISTORY_PERSISTENCE="file"
```

Use the local MySQL development database for the product mode:

```powershell
$env:AGENT_HISTORY_PERSISTENCE="mysql"
$env:AGENT_HISTORY_JDBC_URL="jdbc:mysql://127.0.0.1:3306/jenda_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai"
$env:AGENT_HISTORY_USERNAME="root"
$env:AGENT_HISTORY_PASSWORD="123456"
```

The application creates only the JendaAgent product tables in this database. Existing upstream demo tables are not required for the agent history module.

## JWT Configuration

Local compatibility mode leaves auth disabled and assigns requests to `local-demo-user`:

```powershell
$env:AGENT_AUTH_ENABLED="false"
```

For the product demo, enable it and use one long, private random secret. Do not commit it:

```powershell
$env:AGENT_AUTH_ENABLED="true"
$env:AGENT_AUTH_JWT_SECRET=([Guid]::NewGuid().ToString("N") + [Guid]::NewGuid().ToString("N"))
$env:AGENT_AUTH_ISSUER="jenda-agent"
$env:AGENT_AUTH_TOKEN_TTL="12h"
```

After backend startup, visit `http://localhost:3000/login`, register an account, then enter `/agent-studio` or `/image-studio`.

## Ownership Rules

1. A session ID is claimed by the first authenticated image upload or run request.
2. A different user receives `403` when trying to reuse the same session ID.
3. Session lists, history replay and Workspace queries return only the current user's rows.
4. Existing historical data is marked `legacy-import`, so it is not exposed to newly registered users.

## Verification

1. Register user A and run an OCR or image task.
2. Log out by clearing browser storage or use a private browser window; register user B.
3. Confirm user B cannot see A's session in `/agent-studio`.
4. Call A's session history using B's JWT and confirm HTTP `403` or `404` from user-scoped workspace APIs.

## Follow-up Work

- Replace the lightweight JDBC repository with formal MyBatis-Flex entities and Flyway migrations.
- Add refresh tokens, logout/revocation and SMS verification through Tencent Cloud SMS.
- Persist media metadata and attach each COS object to its owner and session.
- Add rate limits and audit logs for model/tool calls.
