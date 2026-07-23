# Step 32: Docker Compose and Nginx Demo Stack

## Architecture

```text
Browser -> Nginx:80 -> Showcase:3000
                   -> Backend:8080 -> MySQL:3306
                                      -> Redis:6379
                                      -> COS and model providers
```

Only Nginx exposes a host port. MySQL, Redis, Next.js, and Spring Boot remain on
the private Compose network. Nginx disables proxy buffering for `/api/`, so SSE
agent events remain streamed to the browser.

## First Run

```powershell
cd F:\JendaAgent\JendaAgent
Copy-Item .\deploy\.env.example .\deploy\.env
notepad .\deploy\.env
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml ps
```

Open `http://localhost` when `HTTP_PORT=80`, or `http://localhost:<HTTP_PORT>`.
The root route checks the browser's local JWT session and redirects to `/login`
or the real `/agent-studio` product page. The previous root-only visual mock is
not part of the deployed product flow.

## Operations

```powershell
# Stream logs for troubleshooting.
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml logs -f nginx backend

# Stop without removing MySQL and Redis volumes.
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml down

# Rebuild after source changes.
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```

## Security

- `deploy/.env` is ignored by Git and must contain real secrets only on the host.
- Do not expose MySQL or Redis ports on the ECS security group.
- Add HTTPS before public access; Nginx TLS and certificate automation are the
  next deployment step.
