# Step 73: Native Toolbox Providers

## Delivered

- `VolcengineSeedVr2ToolProvider` powers `image-upscale` and `seedvr2` without exposing API keys or COS credentials to the browser.
- `SemanticLayerImageToolProvider` accepts semantic segmentation responses and archives every returned layer as a separate owned COS asset.
- Tool workflow requests now accept `modelProvider`; the toolbox presents only compatible choices for each tool.
- Creative and e-commerce tools keep the existing `SeedDream`, `Qwen`, `NanoBanana 2`, and `NanoBanana Pro` provider route.
- All native outputs emit normal SSE `tool_result` and `image` events and persist the asset/run/session relationship.

## Runtime Selection

| Tool | UI option | Backend provider |
| --- | --- | --- |
| Image Upscale | SeedVR2 | `VolcengineSeedVr2ToolProvider` |
| SeedVR2 Enhance | SeedVR2 | `VolcengineSeedVr2ToolProvider` |
| Image Layering | Semantic Layer / SAM | `SemanticLayerImageToolProvider` |
| Product, poster, character and sticker workflows | Auto, SeedDream, Qwen, NanoBanana | `ImageModelProviderRouter` |

`Auto` preserves a configured `AGENT_TOOLS_*` endpoint first, then selects an enabled native provider for the compatible tool. An explicit native selection fails with an actionable configuration message instead of silently using a different model.

## Required Configuration

Copy the new `AGENT_GATEWAY_SEEDVR2_*` and `AGENT_GATEWAY_SEMANTIC_LAYER_*` entries from `deploy/.env.example` to `deploy/.env`.

- SeedVR2 requires a Volcano Ark API key and a provisioned SeedVR2 model endpoint ID.
- Semantic Layer requires a segmentation vendor endpoint supporting the normalized JSON request and a response that includes layer image URLs or Base64 data.
- Never commit `deploy/.env`.

## Verification

```powershell
cd F:\JendaAgent\JendaAgent\genie-backend
mvn -q -DskipTests compile
```

For Docker deployment, build the executable Spring Boot JAR using `mvn -q -DskipTests package spring-boot:repackage`, then use the established container replacement procedure. Avoid a full Docker rebuild when Docker Desktop is unstable.

## Follow-up

- Add a vendor-specific SAM2 adapter once the chosen segmentation provider and response contract are finalized.
- Add per-model cost and quota controls for SeedVR2 and semantic layering.
- Add integration mocks for native provider success, timeout, and multi-layer archival paths.