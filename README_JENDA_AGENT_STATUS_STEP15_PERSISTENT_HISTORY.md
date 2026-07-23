# Step 15 Addendum: Persistent Development History

## Why Replay Was Empty

The upstream application defaults to `jdbc:h2:mem:genie`. Its data disappears when Spring Boot stops, so the history API correctly returned an empty list after a restart.

## Delivered

- Added the `persistent` Spring profile at `genie-backend/src/main/resources/application-persistent.yml`.
- The profile changes H2 from in-memory storage to `genie-backend/runtime/genie.mv.db`.
- The profile keeps H2 MySQL-compatibility mode and upstream demo-data initialization, avoiding a risky immediate replacement of the upstream DataAgent primary database.
- Runtime database files are ignored by Git.

## Start Command

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
$env:SPRING_PROFILES_ACTIVE="persistent"
mvn -s F:\JendaAgent\JendaAgent\tools\maven-central-settings.xml `
  "-Dmaven.repo.local=F:\JendaAgent\.m2-central" `
  spring-boot:run `
  "-Dspring-boot.run.arguments=--server.port=19090"
```

Run an agent task, restart the backend with the same profile, then call the history API or open `/history-replay`. The events must remain.

## Planned MySQL Migration

The original project uses the default datasource for its H2 DataAgent demo tables. The correct production migration is to add a dedicated MySQL datasource for JendaAgent users, sessions, events, workspace assets, and credentials, then migrate each bounded context without changing the upstream demo datasource in one step.
