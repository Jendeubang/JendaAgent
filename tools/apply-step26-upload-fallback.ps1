$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "showcase\lib\cosDirectUpload.ts"
$content = [System.IO.File]::ReadAllText($path)

if ($content.Contains("uploadImageViaServer")) {
    Write-Host "Upload fallback is already present."
    exit 0
}

$pattern = '(?s)/\*\* Uploads directly to COS; the permanent CAM key never reaches the browser\. \*/\s*export async function uploadImageDirect.*\z'
$matches = [regex]::Matches($content, $pattern)
if ($matches.Count -ne 1) {
    throw "Expected one direct-upload function block, found $($matches.Count). No files were changed."
}

$replacement = @'
async function uploadImageViaServer(apiBaseUrl: string, file: File): Promise<DirectUploadedAsset> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(apiBaseUrl + "/api/v1/agent/media/images", {
    method: "POST",
    body: form,
  });
  if (!response.ok) throw new Error("Server upload fallback failed: " + response.status);
  return await response.json() as DirectUploadedAsset;
}

/**
 * Uploads directly to COS with short-lived STS credentials. If a browser proxy or COS CORS rule blocks the PUT,
 * the same file falls back to the authenticated backend COS uploader without exposing permanent credentials.
 */
export async function uploadImageDirect(apiBaseUrl: string, sessionId: string, file: File): Promise<DirectUploadedAsset> {
  try {
    const ticketResponse = await fetch(apiBaseUrl + "/api/v1/agent/media/direct/tickets", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId, fileName: file.name, mediaType: file.type, size: file.size }),
    });
    if (!ticketResponse.ok) throw new Error("STS ticket request failed: " + ticketResponse.status);
    const ticket = await ticketResponse.json() as DirectUploadTicket;

    const cosResponse = await fetch(ticket.uploadUrl, {
      method: "PUT",
      headers: {
        "Content-Type": file.type,
        "x-cos-security-token": ticket.credentials.token,
        Authorization: await signCosPut(ticket, file.type),
      },
      body: file,
    });
    if (!cosResponse.ok) throw new Error("COS direct upload failed: " + cosResponse.status);

    const completeResponse = await fetch(apiBaseUrl + "/api/v1/agent/media/direct/complete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ uploadId: ticket.uploadId }),
    });
    if (!completeResponse.ok) throw new Error("Upload verification failed: " + completeResponse.status);
    return await completeResponse.json() as DirectUploadedAsset;
  } catch (directError) {
    console.warn("COS direct upload failed; using backend upload fallback.", directError);
    return uploadImageViaServer(apiBaseUrl, file);
  }
}
'@

$updated = [regex]::Replace($content, $pattern, $replacement, 1)
[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"
Write-Host "Step 26 upload fallback completed. Restart the frontend."
