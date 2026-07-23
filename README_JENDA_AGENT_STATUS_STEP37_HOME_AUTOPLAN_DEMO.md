# Step 37: Home AutoPlan Demo

## Purpose

The `/zh` home page now has an automatically cycling AutoPlan execution panel, based on the product interaction pattern of a multi-step agent run.

## Behavior

- A local timer advances one stage every 2.2 seconds.
- Completed stages show `Complete`; the current stage shows `Running`; later stages show `Waiting`.
- The panel loops after final optimization and updates its run-cycle indicator.
- The final asset card uses a progress animation only during the image-generation stage.

## Important Boundary

This is a homepage product demonstration and does not call a model or poll an API. Real task progress remains on `/zh/agent`, where the UI is driven by the existing SSE stream from the backend.

## Verification

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build
```

After deployment, open `http://localhost/zh` and wait about 2 seconds to observe the active step advance.
