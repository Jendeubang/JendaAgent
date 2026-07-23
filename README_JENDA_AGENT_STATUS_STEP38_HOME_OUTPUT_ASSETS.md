# Step 38: Home Completion State And Public Assets

## Fixed

- The homepage AutoPlan panel now adds a dedicated completion phase after the last execution stage.
- Completion shows `任务完成`, `output_poster_final.png`, and a delivered-final-output message before the demo starts the next run cycle.
- The showcase runtime Docker image now copies `/app/public` so Next.js can serve `/crispix/*` image assets after container deployment.

## Root Cause

The previous runtime image copied `.next` and `node_modules` but did not copy the Next.js `public/` directory. The page markup referenced `/crispix/tool-01.webp`, which therefore returned `404` from the deployed container.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build

cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml build showcase
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up -d --no-deps showcase
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml restart nginx
```

The expected verification endpoint is `http://localhost/crispix/tool-01.webp` with HTTP 200 and `image/webp`.
