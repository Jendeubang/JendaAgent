# Step 62 - Production Image Visibility Fix

## Root Cause

Generated images were archived in `agent_asset`, but the workspace query only read legacy `agent_image_message` records. New ReAct and Plan-Solve runs therefore had valid COS assets that were not returned to `/zh/agent`.

Some legacy generated asset rows also had an empty `object_key`. Their signed COS URLs expire, so a reload could show a broken image even though the object still existed.

## Fix

- Workspace generated outputs now come from the authoritative `agent_asset` table, scoped by `owner_user_id` and `session_id`.
- Upload references continue to come from the run input list and are not mixed into generated outputs.
- New `recordGeneratedForSession` records derive the `object_key` from a Jenda COS URL and immediately replace the stored URL with a fresh signed GET URL.
- Historical records without `object_key` recover it from a URL belonging to the configured COS bucket and re-sign it while reading the workspace.
- Third-party provider URLs are not treated as COS URLs and continue to use their original URL.

## Validation

The latest COS generated image was verified with an HTTP `GET 200`. The prior `HEAD 403` does not affect browser image rendering because image elements use GET.

Focused tests:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dtest=CosSignedUrlServiceTest,AgentRuntimeClientsTest" test
```

## Deployment

Rebuild the stack after this change:

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```

Open `/zh/agent`, then either reload the current session or create a new image task. Generated images should appear in the delivery cards and remain available after a page reload.