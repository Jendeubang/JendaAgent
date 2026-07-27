# Step 60 - Testing And Observability

## Delivered

- Spring Boot Actuator endpoints: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, and `/actuator/metrics`.
- Docker backend health check and Nginx dependency on backend readiness.
- `X-Request-Id` request tracing. A caller-provided safe ID is preserved; otherwise the backend generates a UUID and writes it to the response and log MDC.
- JSON console logs through `logstash-logback-encoder`. Important request fields include `requestId`, `userId` when authenticated, method, path, status, and duration.
- Optional HTTPS webhook notifications for HTTP 5xx responses. Alerting is disabled by default.
- Unit tests for refresh cookies, request tracing, model/tool disabled behavior, and duplicate tool request locking.
- Optional Testcontainers integration contract for MySQL 8.4 and Redis 7.4.
- Playwright E2E baseline for login and authenticated entry into `/zh/agent` using mocked auth APIs.

## Local Verification

Run backend focused tests:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dtest=AgentRuntimeClientsTest,AgentToolIdempotencyServiceTest,AgentRefreshCookieServiceTest,RequestTraceFilterTest" test
```

Run Docker-backed infrastructure contract after Docker Desktop is healthy:

```powershell
mvn "-Dtest=AgentContainerContractsIT" test
```

Build the frontend:

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm build
```

Install the Playwright browser once. This downloads Chromium and may need a stable network/proxy:

```powershell
corepack pnpm exec playwright install chromium
corepack pnpm test:e2e
```

## Deployment Health Check

After rebuilding the deployment, verify from inside the private backend container:

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

The response must contain `status: UP`. The backend container will show `healthy` after its readiness probe succeeds:

```powershell
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml ps
```

## Optional Failure Alert Webhook

Do not commit webhook credentials. Add these only to `deploy/.env`:

```dotenv
AGENT_ALERT_ENABLED=true
AGENT_ALERT_WEBHOOK_URL=https://your-alert-endpoint.example/hooks/jenda
```

Requirements:

- The URL must use HTTPS; non-HTTPS values are ignored deliberately.
- The payload contains event name, request ID, method, path, HTTP status, and duration. It never includes API keys, JWTs, prompts, image URLs, or request bodies.
- Restart/rebuild the backend after changing `deploy/.env`.

## Test Scope And Next Work

| Layer | Current automated coverage | Next expansion |
| --- | --- | --- |
| Unit | Tool routing disabled state, idempotency, refresh-cookie security, request tracing | Add direct tests for DAG replan decisions and asset ownership queries |
| Integration | MySQL/Redis Testcontainers contract | Wire Spring context to Testcontainers and add an SSE streaming assertion |
| Provider | Existing client unit behavior | Add MockWebServer fixtures for Qwen, SeedDream, Gemini, COS STS, and COS archive failure paths |
| E2E | Mocked login -> `/zh/agent` | Add authenticated upload, OCR, generation, editing, history replay, and asset deletion with isolated test accounts |

## Operational Rules

- Treat `/actuator/health` as an internal endpoint in public production deployments. Nginx currently exposes `/api/*`; do not add a public reverse proxy route for Actuator without network restrictions.
- Use the returned `X-Request-Id` when investigating a browser failure. Search the backend JSON logs using that ID.
- A model/COS/SMS end-to-end test must use dedicated sandbox credentials and an isolated COS prefix. Never run paid model tests against production credentials from a shared CI branch.