# Step 61 - Image Delivery Experience

## Delivered

- The `/zh/agent` result panel now shows generated images as explicit Jenda delivery cards instead of only recording them in COS.
- Each output card supports full-size preview, copy, and download.
- Copy first attempts to put the image blob on the clipboard. If the browser or COS CORS rules prevent that, it copies the signed image URL instead.
- Download first fetches the image blob and uses a browser download. If that cannot be fetched, it opens the signed URL in a new tab for manual saving.
- Reference images remain available in the compact asset grid. Clicking any reference or generated image opens the same full-size preview dialog.
- Workspace reads now prefer `agent_asset.object_key` and issue a new COS signed GET URL at read time. This prevents history replay from rendering expired COS URLs after a backend restart.

## User Flow

1. Open `/zh/agent` and create an image generation or image editing task.
2. Wait for the `image` event and final summary.
3. Under the task timeline, open the `Task delivery` card.
4. Choose `Preview` to inspect the full image, `Copy` to paste it into another application, or `Download` to save it locally.
5. Refresh the page or reopen the historical session. The workspace endpoint re-signs the COS object URL before rendering the image.

## COS Browser Requirements

The COS bucket CORS rule must allow the web origin used by the user, for example:

- Local Docker deployment: `http://localhost:8088`
- Local Next.js development: `http://localhost:3000`
- Production: the exact HTTPS domain of the application

Allow methods: `GET`, `HEAD`, and `PUT` for direct upload. Allow request headers: `*`. Expose headers: `ETag`, `Content-Length`, and `Content-Type`.

If a browser blocks clipboard image access, this is normal browser policy behavior. The UI falls back to copying the temporary signed URL. The Download button still provides a direct image save path.

## Verification

Build the frontend:

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm build
```

Run backend focused tests:

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn "-Dtest=AgentRuntimeClientsTest,AgentToolIdempotencyServiceTest,AgentRefreshCookieServiceTest,RequestTraceFilterTest" test
```

Rebuild the Docker deployment after this stage:

```powershell
cd F:\JendaAgent\JendaAgent
docker compose --env-file .\deploy\.env -f .\deploy\docker-compose.yml up --build -d
```

Then generate or edit one image. Verify preview, copy, download, reload, and history replay using the same user account.

## Follow-up

- Add a backend download proxy if the deployment needs fixed download filenames without relying on COS response headers.
- Add a generated asset gallery filter by run ID and provider.
- Add browser E2E coverage for a mocked output card and blob download.