# Step 30: User Asset Center

## Delivered

- `GET /api/v1/agent/assets?page=1&size=12&sessionId=`: JWT-scoped pagination and optional session filter.
- `DELETE /api/v1/agent/assets/{assetId}`: deletes the owned COS object first, then removes its MySQL metadata.
- `/assets`: image gallery with previews, signed-URL download, session filter, pagination, metadata, and destructive delete confirmation.
- Asset card displays the run ID, date, COS object path, source type and size.
- Account dropdown now links to Asset Center.

## Safety

- Every query and delete is constrained by `owner_user_id` from the verified JWT.
- COS deletion refuses object keys outside `agent.storage.cos.prefix`.
- A failed COS delete leaves the MySQL row intact, so no orphaned asset metadata is silently removed.

## Verification

1. Open `http://localhost:3000/assets`.
2. Filter by one session and confirm only its images are shown.
3. Delete a test image and confirm it disappears from the page.
4. Verify the MySQL row is gone and the COS object returns 404.
