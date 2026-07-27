# Step 70: Collapsible Agent Assets and Reference Reuse

## User behavior

- Uploaded reference images are grouped in a collapsible reference panel.
- Select one or more reference images before sending the next task.
- The run request contains only the selected asset image URLs in `imageUrls`.
- Generated deliverables are grouped in a separate collapsible delivery panel.
- Starting a new run retains reference assets but clears prior generated deliverables from the current display.

## Verification

1. Open `/zh/agent` and upload one or more images.
2. Expand the reference panel, select the images to reuse, and submit an image edit task.
3. Confirm the selected count appears above the sender.
4. Collapse and expand both asset panels; preview, copy, and download remain available for generated images.
