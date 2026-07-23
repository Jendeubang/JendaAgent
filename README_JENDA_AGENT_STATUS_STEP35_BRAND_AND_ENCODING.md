# Step 35: Jenda Brand And Encoding Recovery

## Fixed

- Replaced the public-facing `Crispix` brand with `Jenda` on the shared header, home page, tool page, and pricing page.
- Replaced the old `C` mark with the `J` mark and updated visible product names to `Jenda Agent` and `Jenda Tool`.
- Removed malformed UTF-8/mojibake characters from public `/zh` route sources.
- Moved every visible Chinese string into JavaScript string values so escaped Unicode is decoded by the runtime instead of being rendered as literal `\\uXXXX` text.

## Routes

- `/zh`: Jenda product landing page.
- `/zh/agent`: real Agent Studio and existing SSE/COS/JWT workflows.
- `/zh/tool`: Jenda tool catalog.
- `/zh/pricing`: Jenda pricing view.

## Verification

Run the following after this step:

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build
```

For Docker:

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml restart nginx
```

Restarting Nginx after a front-end container recreation refreshes its upstream IP resolution.
