# Step 36: Jenda Agent Conversation Workspace

## Delivered

- Rebuilt `/zh/agent` as the public Jenda Agent experience, following the interaction structure observed on the Crispix Agent reference page while retaining Jenda branding.
- Added Ant Design X `XProvider`, `Sender`, and `Bubble.List` for the AI conversation surface.
- Preserved real product behavior: existing session identifier persistence, direct COS reference-image upload, agent run submission, SSE event rendering, generated image output, and Workspace asset loading.
- Added visual prompt chips, reference-image attachment, execution-mode toggle, responsive layout, and generated-result workspace cards.

## Architecture

`/zh/agent` -> `agentFetch` -> `/api/v1/agent/sessions/{sessionId}/runs` -> SSE event stream -> Ant Design X bubble list

`/zh/agent` -> `uploadImageDirect` -> STS/COS -> image URL -> agent run request

## Local Validation

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build

cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml restart nginx
```

Open `http://localhost/zh/agent` after the containers are healthy.

## Follow-up Improvements

- Add a dedicated mobile screenshot QA pass against the approved reference.
- Add a compact session switcher to the public Agent screen when the interaction needs multi-session navigation.
- Add tool-result cards for OCR, generation, and edit actions using Ant Design X thought-chain components.
