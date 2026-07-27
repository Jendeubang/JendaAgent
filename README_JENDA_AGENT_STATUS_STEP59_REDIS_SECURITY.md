# JendaAgent Step 59: Redis and Security Operations

## Delivered

- Redis-first state for login failures, SMS codes, refresh-token rotation, JWT blacklist and tool idempotency locks.
- JDBC remains the local-development and transient Redis outage fallback for login, SMS and token state.
- Refresh Token rotation now uses a configurable `HttpOnly` cookie. The response body no longer exposes it by default.
- CAPTCHA endpoint: `GET /api/v1/auth/captcha`. Enable it with `AGENT_AUTH_CAPTCHA_ENABLED=true`.
- Login page sends cross-origin cookies safely with `credentials: include` and displays a reloadable CAPTCHA challenge when available.
- `agent_security_audit`, `agent_user_plan`, and `agent_usage_ledger` are created by Flyway migration `V2`.
- Tool requests use a Redis `SETNX` lock, per-user plan quota checks, usage ledger writes, and audit records.
- Account usage: `GET /api/v1/account/usage`.
- Administrator plan update: `POST /api/v1/admin/users/{userId}/plan`, body `{"planCode":"PRO"}`. The signed-in username must be listed in `AGENT_AUTH_ADMIN_USERNAMES`.

## Deployment settings

Add these values to `deploy/.env`:

```dotenv
AGENT_REDIS_ENABLED=true
AGENT_AUTH_REFRESH_COOKIE_NAME=jenda_refresh
AGENT_AUTH_REFRESH_COOKIE_SECURE=true
AGENT_AUTH_REFRESH_COOKIE_SAME_SITE=Lax
AGENT_AUTH_EXPOSE_REFRESH_TOKEN_IN_BODY=false
AGENT_AUTH_CAPTCHA_ENABLED=true
AGENT_AUTH_ADMIN_USERNAMES=your-admin-username
```

`AGENT_AUTH_REFRESH_COOKIE_SECURE` must be `true` when the public site is HTTPS. Set it to `false` only for direct local HTTP testing. CAPTCHA deliberately fails closed when enabled but Redis is unavailable.

## Data lifecycle

```text
login failure -> Redis counter / lock -> JDBC fallback
SMS code -> Redis one-time hash -> JDBC audit fallback
login / refresh -> opaque refresh token cookie -> Redis one-time consume -> JDBC fallback
logout -> JWT jti blacklist in Redis + JDBC persistence
agent tool -> Redis idempotency lock -> plan quota -> provider -> usage ledger + audit
```

## Remaining work

- Add administrator UI for usage trends and plan changes.
- Add CAPTCHA image distortion / third-party risk scoring for public scale.
- Replace simple provider cost estimates with provider billing reconciliation.
- Add scheduled retention cleanup for historical JDBC fallback rows and audit records.