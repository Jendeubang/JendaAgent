# Step 77 - Gemini NanoBanana 2 Smoke Readiness

## Fixed request compatibility

Google Gemini Interactions currently rejected `response_format.mime_type=image/png` for the configured NanoBanana 2 request. The image adapter now sends `image/jpeg` for Gemini image output regardless of an older PNG environment value. COS storage and signed delivery already support JPEG assets.

Default configuration is now:

```env
AGENT_GATEWAY_GEMINI_OUTPUT_MIME_TYPE=image/jpeg
```

## Smoke attempt on 2026-07-28

- The backend was rebuilt and started healthy.
- The prior invalid output MIME request was fixed.
- A real NanoBanana 2 request reached Google Gemini successfully but received HTTP 403 with `permission_denied`: the configured Google project has been denied access.
- No image was generated and no further request was retried.

## Required account-side action

1. In Google AI Studio, create or select a project with Gemini API access.
2. Create an AI Studio Gemini API key for that project, restricted to Gemini API.
3. Enable billing when the project or image model requires a paid tier.
4. Replace only `AGENT_GATEWAY_GEMINI_API_KEY` in `deploy/.env`.
5. Recreate the backend container and rerun a NanoBanana 2 generation request.

The application keeps NanoBanana Pro disabled until the NanoBanana 2 generation and edit smoke checks both pass.