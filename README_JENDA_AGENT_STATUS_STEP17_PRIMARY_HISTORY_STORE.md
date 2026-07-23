# Step 17: Primary Persistent History Repository

## Fix

The reflection-based history `JdbcTemplate` injection was not reliable for bean construction order. `PersistentAgentHistoryStore` now extends `AgentHistoryStore` and is marked `@Primary` when `agent.history.persistence=file`.

Spring injects this repository into both the runtime and history controller. Its inherited schema initializer creates the same event tables in `runtime/agent-history.mv.db`.

## Required Startup Environment

```powershell
Remove-Item Env:\SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
$env:AGENT_HISTORY_PERSISTENCE="file"
```

Do not use the old `persistent` Spring profile. After restarting the backend, submit one new agent task. Events created before this change were in memory and cannot be recovered.

## Verification SQL

After the new task completes and the backend stops, this query must return a positive value:

```powershell
java -cp "F:\JendaAgent\.m2-central\com\h2database\h2\2.2.224\h2-2.2.224.jar" org.h2.tools.Shell `
  -url "jdbc:h2:file:./runtime/agent-history;MODE=MySQL" `
  -user sa `
  -sql "SELECT COUNT(*) AS event_count FROM agent_message"
```
