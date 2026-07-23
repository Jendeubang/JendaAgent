# Step 53: Gemini NanoBanana Provider

## Status

Implemented a server-only Gemini image provider under the existing image-provider abstraction. It supports text-to-image and reference-image editing through the normalized internal tool routes:

- `gemini-nano-banana-2`: Gemini fast image generation/editing route.
- `gemini-nano-banana-pro`: high-quality route. Disabled by default.
- `nano-banana-2` and `nano-banana-pro`: accepted backend aliases.

The browser sends only the selected provider identifier. It never receives the Gemini key or raw COS credentials.

## Execution flow

1. The authenticated agent request creates an SSE run.
2. The async ReAct or Plan-Solve executor retains the authenticated `ownerUserId`.
3. `HttpAgentToolClient` attaches that server-derived owner to its same-container internal gateway request.
4. `GeminiImageProvider` reserves the daily quota for that owner and model.
5. Existing COS reference URLs are verified as managed objects, downloaded by the backend using a COS signed GET URL, and encoded as Base64 for Gemini.
6. Gemini output Base64 is decoded and written directly to COS by the backend.
7. Only a signed COS URL is returned in SSE and persisted as a workspace asset.

## Configuration

Copy the Gemini block from `deploy/.env.example` into `deploy/.env` and set:

```dotenv
AGENT_GATEWAY_GEMINI_ENABLED=true
AGENT_GATEWAY_GEMINI_API_KEY=your_google_gemini_api_key
AGENT_GATEWAY_GEMINI_PRO_ENABLED=false
```

Use `NanoBanana 2` from `/zh/agent` first. Once billing and access are verified, set `AGENT_GATEWAY_GEMINI_PRO_ENABLED=true` to expose NanoBanana Pro. The default limits are 20 NanoBanana 2 calls and 3 Pro calls per authenticated user per day. The Pro route falls back to NanoBanana 2 only for transport, rate-limit, or server failures when `AGENT_GATEWAY_GEMINI_PRO_FALLBACK_TO_NANO_BANANA2=true`.

## Reliability constraints

- Pro is denied while its server switch is disabled; it cannot silently create premium cost.
- Quota is released for failed calls and retained only after a provider produces an archived COS result.
- Image inputs are restricted to managed COS URLs, preventing arbitrary server-side URL fetches.
- The provider has configurable input count, image byte, and API timeout limits.
- Live Gemini validation requires a real API key and reachable Google endpoint. The local build validates the integration shape but cannot validate a third-party account.

## Follow-ups

- Add an admin quota dashboard and monthly budget alarms.
- Add provider health checks and circuit breaker metrics.
- Add per-model aspect ratio and output-format controls when the selected Gemini model supports them.