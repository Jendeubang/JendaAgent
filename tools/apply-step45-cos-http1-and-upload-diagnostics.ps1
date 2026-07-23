$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$cosPath = Join-Path $root 'genie-backend\src\main\java\com\jd\genie\service\agent\CosAgentImageStorage.java'
$frontPath = Join-Path $root 'showcase\lib\cosDirectUpload.ts'

function Replace-Once([string]$Path, [string]$Old, [string]$New) {
  $content = [System.IO.File]::ReadAllText($Path)
  $count = ([regex]::Matches($content, [regex]::Escape($Old))).Count
  if ($count -ne 1) { throw "Expected exactly one matching block in $Path, found $count. No file was changed." }
  [System.IO.File]::WriteAllText($Path, $content.Replace($Old, $New), [System.Text.UTF8Encoding]::new($false))
  Write-Host "Updated $Path"
}

Replace-Once $cosPath 'import okhttp3.OkHttpClient;' "import okhttp3.OkHttpClient;`r`nimport okhttp3.Protocol;"
Replace-Once $cosPath 'import java.util.Locale;' "import java.util.List;`r`nimport java.util.Locale;"
Replace-Once $cosPath '            .retryOnConnectionFailure(true)`r`n            .build();' '            .retryOnConnectionFailure(true)`r`n            // Tencent COS intermittently closes HTTP/2 upload streams from Docker Desktop.`r`n            .protocols(List.of(Protocol.HTTP_1_1))`r`n            .build();'
Replace-Once $cosPath '                    .header("Authorization", authorization)`r`n                    .put(RequestBody.create(bytes, MediaType.get(contentType)))' '                    .header("Authorization", authorization)`r`n                    .header("Connection", "close")`r`n                    .put(RequestBody.create(bytes, MediaType.get(contentType)))'

$frontContent = [System.IO.File]::ReadAllText($frontPath)
$catchPattern = '    console\.warn\("COS direct upload failed; using backend upload fallback\."\, directError\);\r?\n    return uploadImageViaServer\(apiBaseUrl, sessionId, file\);'
$catchCount = ([regex]::Matches($frontContent, $catchPattern)).Count
if ($catchCount -ne 1) { throw "Expected exactly one direct upload fallback block in $frontPath, found $catchCount. No file was changed." }
$catchReplacement = @(
  '    console.warn("COS direct upload failed; using backend upload fallback.", directError);',
  '    try {',
  '      return await uploadImageViaServer(apiBaseUrl, sessionId, file);',
  '    } catch (fallbackError) {',
  '      const fallbackMessage = fallbackError instanceof Error ? fallbackError.message : "Server upload fallback failed";',
  '      throw new Error(`Image upload failed. Direct upload: ${message}. Fallback: ${fallbackMessage}`);',
  '    }'
) -join [Environment]::NewLine
$frontContent = [regex]::Replace($frontContent, $catchPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $catchReplacement })
[System.IO.File]::WriteAllText($frontPath, $frontContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $frontPath"

$readme = Join-Path $root 'README_JENDA_AGENT_STATUS_STEP45_COS_HTTP1_DIAGNOSTICS.md'
$readmeContent = @'
# Step 45: COS HTTP/1.1 Upload and Diagnostics

## Change

Docker-side COS uploads now force OkHttp HTTP/1.1 and `Connection: close`. This avoids intermittent closed HTTP/2 upload streams observed when Docker Desktop uploads to COS.

## Diagnostics

When browser COS direct upload and backend fallback both fail, the UI now shows both causes. Typical direct-upload CORS failure will include `Failed to fetch`; ensure COS allows the `http://localhost` origin with `PUT` allowed.

## Verification

- Rebuild the backend and showcase images.
- Upload through `/zh/agent`.
- Inspect backend logs only if the combined error still reports a fallback failure.
'@
[System.IO.File]::WriteAllText($readme, $readmeContent, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $readme"
