# Step 16: Isolated Persistent History Store

## Problem

The upstream `DataAgentConfig` always opens `jdbc:h2:mem:genie`. Changing Spring's primary datasource to a file H2 database split the application into two databases, causing `sales_data not found` during startup.

## Delivered

- Added `PersistentAgentHistoryStoreInjector`.
- When `agent.history.persistence=file`, it redirects only `AgentHistoryStore` to `jdbc:h2:file:./runtime/agent-history;MODE=MySQL` before its schema initialization runs.
- Upstream DataAgent and all original tables remain on their unchanged in-memory H2 datasource.
- Agent event history survives application restarts in `genie-backend/runtime/agent-history.mv.db`.

## Start Command

Do not enable the earlier `persistent` Spring profile. Use the normal profile plus the isolated history setting:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
Remove-Item Env:\SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
$env:AGENT_HISTORY_PERSISTENCE="file"
mvn -s F:\JendaAgent\JendaAgent\tools\maven-central-settings.xml `
  "-Dmaven.repo.local=F:\JendaAgent\.m2-central" `
  spring-boot:run `
  "-Dspring-boot.run.arguments=--server.port=19090"
```

All existing model, COS, STS, and OCR environment variables must remain in this same PowerShell window.

## Verification

1. Complete an agent task.
2. Restart with the exact command above.
3. Call the history API or open `/history-replay`; the previous events must remain.
