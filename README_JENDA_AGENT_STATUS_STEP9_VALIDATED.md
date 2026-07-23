# Step 9 STS Direct Upload Validated

## Status

Tencent COS STS direct upload has been verified manually with the real
`jenda-agent-1455545317` bucket in `ap-shanghai`.

The validated flow is:

1. The frontend requests a direct-upload ticket from the backend.
2. The backend uses the CAM user's permanent COS credentials to request a
   restricted, short-lived federation token.
3. The browser uploads the selected image directly to COS with that temporary
   credential.
4. The backend confirms the object with HeadObject and returns a signed HTTPS
   URL suitable for model access.

The CAM user is attached to `JendaAgentCosServerPolicy`, limited to
`agent/*` and the actions PutObject, GetObject, and HeadObject. No separate
STS action policy is required for GetFederationToken.

## Next Implementation Priority

Connect uploaded assets to real agent runs. The `/live` page should accept
the asset returned by upload, send its signed URL as `image_url` in the run
request, and render the persisted multimodal event timeline. This turns the
standalone upload demo into an end-to-end multimodal ReAct/Plan-Solve flow.

## Later Milestones

1. Persist history in MySQL and add authentication/session ownership.
2. Wire Qwen and image/OCR tool providers through runtime configuration.
3. Add a Workspace page that groups generated assets by session and supports
   preview, metadata, and download.
4. Replace temporary BeanPostProcessor compatibility adapters by modifying
   the upstream controller/service integration points directly.
