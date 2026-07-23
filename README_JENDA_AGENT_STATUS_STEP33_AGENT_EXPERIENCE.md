# Step 33: Crispix-Inspired Agent Studio

## Product Goal

`/agent-studio` is the deployed product workspace. It is inspired by the
creative-agent information architecture of Crispix Agent, but does not copy
its assets, brand, or source. The implementation retains JendaAgent's real
JWT-protected APIs and replaces the old administration-style view with a
creative production workspace.

## Layout

```text
Top navigation: Agent / Image Studio / Assets / mode / account
Left rail: current session and persistent session history
Center canvas: prompt launchpad and streamed Plan-Solve or ReAct trace
Right rail: reference images and generated COS assets for the session
```

## Working Interactions

- Example prompts populate the real task input.
- Upload uses the existing STS-first COS flow and is delivered as `image_url`.
- Sending a task calls the existing SSE run endpoint.
- Streamed `plan`, `task`, `tool_call`, `tool_result`, `image`, and `summary`
  events render as process cards.
- Session selection uses the existing persisted history endpoints in read-only
  mode; the plus button creates a separate session id.
- Workspace cards use signed COS URLs and open/download the actual asset.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build
```

Rebuild the Docker frontend after source changes:

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```
