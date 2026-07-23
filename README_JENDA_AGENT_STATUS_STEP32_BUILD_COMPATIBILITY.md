# Step 32 Build Compatibility Notes

- Frontend container uses Node 22 and pnpm 11.14.0, matching the workspace
  build-policy format and enabling the approved `sharp` build script.
- `pnpm-workspace.yaml` is copied before `pnpm install` so container builds use
  the same dependency policy as local development.
- Backend container uses a BuildKit Maven cache at `/root/.m2`; the first build
  can take several minutes, while later builds reuse downloaded dependencies.
