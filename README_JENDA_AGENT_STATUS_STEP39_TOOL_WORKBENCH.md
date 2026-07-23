# Step 39: Tool Workbench Pages

## Delivered

- Added a reusable dynamic tool workbench at `/zh/tool/[slug]`.
- Added source-grounded layouts for:
  - `/zh/tool/image-upscale`
  - `/zh/tool/seedvr2`
  - `/zh/tool/image-layered`
  - `/zh/tool/product-refinement`
- Updated the Tool page cards to route to individual workbenches.
- Updated the shared navigation to keep `工具箱` active on all nested tool pages.

## Interaction Model

- Image selection displays a real local thumbnail and file name.
- Tool-specific settings update the JSON request preview in real time.
- Start processing shows a loading state, then shows the uploaded image as a front-end result preview with download action.
- The empty result, processing, output, delete-upload, desktop two-column, and mobile stacked states are implemented.

## Backend Boundary

The platform currently has image generation, image edit, and OCR gateways. It does not yet expose dedicated backend adapters for image upscale, SeedVR2 enhancement, image layering, or product refinement. The new workbench is therefore product-complete on the front end; its `processImage` function is the future integration point for those tool gateways.

## Validation

```powershell
cd F:\JendaAgent\JendaAgent\showcase
corepack pnpm exec tsc --noEmit --skipLibCheck
corepack pnpm build
```

## Reference Evidence

The design was built from the user-provided desktop and mobile screenshots for the four referenced Crispix tool workflows. Browser screenshot QA is unavailable in this task, so visual QA remains blocked pending an agent-controllable browser or user feedback screenshots of the Jenda implementation.
