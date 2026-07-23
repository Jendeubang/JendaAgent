# Step 42: Authenticated Reference Image Upload

## Fix

The `/zh/agent` reference-image uploader now uses the shared `agentFetch` wrapper for STS ticket creation, upload completion, and the server-side fallback. The wrapper attaches the JWT, refreshes an expired access token once, then retries the request.

## Agent Image Workflow

1. Sign in at `/login`.
2. Open `/zh/agent` and click the left reference-image upload button.
3. COS returns an image URL, which is attached to the agent task.
4. Enter an instruction such as `Replace the background with a rainy cyberpunk street` or `Recognize all text in this image`.
5. Optionally select Image Editing or OCR in Model Preference, then submit.

The backend receives `{ prompt, mode, imageUrls, preferredTools }`; the selected model/tool chain can use the uploaded image as multi-modal context.

## Error Semantics

- Expired session: the browser redirects to `/login` after refresh-token retry fails.
- COS or STS transport failure: upload falls back to the authenticated backend endpoint.
- Successful upload: the COS URL is passed into agent planning and execution.