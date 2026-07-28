# Step 79 - Nginx Dynamic Docker DNS

## Incident

After the frontend container was recreated, Nginx retained the old `showcase` container IP and returned HTTP 502 while connecting to port 3000.

## Fix

`deploy/nginx/default.conf` now uses Docker DNS (`127.0.0.11`) with a ten-second validity period and variable-backed upstream names for both `backend` and `showcase`. Nginx therefore resolves the current Compose service address at request time instead of retaining a container IP from its initial startup.

## Verification

After recreating Nginx, `GET http://127.0.0.1:8088/zh/agent` returned HTTP 200.

## Operational Result

Rebuilding or recreating the backend or showcase service no longer requires a manual Nginx restart to recover from stale upstream container IP addresses.