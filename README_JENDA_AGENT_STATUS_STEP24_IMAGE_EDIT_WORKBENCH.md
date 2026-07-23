# Step 24: Image Edit Workbench

## Route

`/image-studio`

## Delivered

- Direct COS uploads for a source image and optional mask image.
- Source image is sent as `imageUrls[0]`; mask image is sent as `imageUrls[1]`.
- The edit prompt explicitly instructs the model to edit white mask areas and preserve black mask areas.
- Uses the existing dynamic Plan-Solve SSE endpoint and Qwen image-edit gateway.
- Displays a draggable before/after comparison slider from the real source and COS archived output.
- Lists persisted generated assets through the existing Workspace API.
- Includes COS signed URL preview, browser download, dimensions, asset ID, and run ID.

## Test

1. Start the backend with Qwen image-edit, COS, and persistent history variables.
2. Start the frontend and open `http://localhost:3000/image-studio`.
3. Upload a source image.
4. Optionally upload a black-and-white mask.
5. Enter an edit direction, then run image edit.
6. Confirm the final trace says `archived to COS`, then use the inspector download action.

## Mask Constraint

The Qwen API accepts up to three input images but has no separate mask field in this integration. The workbench sends the mask as the second reference image together with clear white-edit/black-preserve instruction text. This is suitable for guided local edits; pixel-perfect inpainting is a future provider-specific enhancement.
