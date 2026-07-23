$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root 'showcase\lib\cosDirectUpload.ts'
$content = [System.IO.File]::ReadAllText($path)

function Replace-Once([string]$Old, [string]$New) {
  $count = ([regex]::Matches($script:content, [regex]::Escape($Old))).Count
  if ($count -ne 1) { throw "Expected exactly one matching block in $path, found $count. No file was changed." }
  $script:content = $script:content.Replace($Old, $New)
}

Replace-Once 'import { agentHeaders } from "./agentAuth";' 'import { agentFetch } from "./agentAuth";'
Replace-Once '  const response = await fetch(apiBaseUrl + "/api/v1/agent/media/images", { method: "POST", headers: agentHeaders(), body: form });' '  const response = await agentFetch(apiBaseUrl + "/api/v1/agent/media/images", { method: "POST", body: form });'
Replace-Once '    const ticketResponse = await fetch(apiBaseUrl + "/api/v1/agent/media/direct/tickets", {' '    const ticketResponse = await agentFetch(apiBaseUrl + "/api/v1/agent/media/direct/tickets", {'
Replace-Once '      method: "POST", headers: agentHeaders({ "Content-Type": "application/json" }),' '      method: "POST", headers: { "Content-Type": "application/json" },'
Replace-Once '    const completeResponse = await fetch(apiBaseUrl + "/api/v1/agent/media/direct/complete", { method: "POST", headers: agentHeaders({ "Content-Type": "application/json" }), body: JSON.stringify({ uploadId: ticket.uploadId }) });' '    const completeResponse = await agentFetch(apiBaseUrl + "/api/v1/agent/media/direct/complete", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ uploadId: ticket.uploadId }) });'

$catchPattern = '  \} catch \(directError\) \{\r?\n    console\.warn\("COS direct upload failed; using backend upload fallback\."\, directError\);\r?\n    return uploadImageViaServer\(apiBaseUrl, sessionId, file\);\r?\n  \}'
$catchCount = ([regex]::Matches($content, $catchPattern)).Count
if ($catchCount -ne 1) { throw "Expected exactly one direct upload catch block in $path, found $catchCount. No file was changed." }
$catchReplacement = @(
  '  } catch (directError) {',
  '    const message = directError instanceof Error ? directError.message : "Direct upload failed";',
  '    // Authentication errors are not transport failures. agentFetch already retried a token refresh.',
  '    if (message.includes(": 401")) throw new Error("\\u767b\\u5f55\\u5df2\\u8fc7\\u671f\\uff0c\\u8bf7\\u91cd\\u65b0\\u767b\\u5f55\\u540e\\u4e0a\\u4f20\\u56fe\\u7247");',
  '    console.warn("COS direct upload failed; using backend upload fallback.", directError);',
  '    return uploadImageViaServer(apiBaseUrl, sessionId, file);',
  '  }'
) -join [Environment]::NewLine
$content = [regex]::Replace($content, $catchPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $catchReplacement })
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"

$readme = Join-Path $root 'README_JENDA_AGENT_STATUS_STEP42_AUTHENTICATED_UPLOAD.md'
$readmeContent = @'
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
'@
[System.IO.File]::WriteAllText($readme, $readmeContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $readme"
