# Step 22: Generated Image COS Archive

## Goal

Persist Qwen Image result URLs before the provider's temporary URL expires.

## Flow

```text
Qwen Image response URL
  -> allowlisted HTTPS download from aliyuncs.com
  -> 10 MB content-type validation
  -> existing CosAgentImageStorage upload
  -> existing CosSignedUrlService signed GET URL
  -> tool_result, image event, history, and Workspace use COS URL
```

## Failure Behavior

Image generation remains successful when COS archival fails. The internal tool response keeps the provider URL and reports `COS archive fallback` in its text field.

## Prerequisites

Existing COS environment variables are sufficient:

```powershell
$env:AGENT_STORAGE_PROVIDER="cos"
$env:AGENT_STORAGE_COS_BUCKET="jenda-agent-1455545317"
$env:AGENT_STORAGE_COS_REGION="ap-shanghai"
$env:AGENT_STORAGE_COS_SECRET_ID="..."
$env:AGENT_STORAGE_COS_SECRET_KEY="..."
$env:AGENT_STORAGE_COS_PREFIX="agent/"
```

## Apply and Verify

1. Run `tools/apply-step22-generated-image-cos-archive.ps1`.
2. Rebuild and restart the backend in the same terminal containing the COS and Qwen variables.
3. Submit a new image-generation task in `/plan-solve`.
4. Expected `tool_result` text contains `archived to COS`, `provider` is `qwen-image-cos`, and the displayed image URL is a COS signed URL.
