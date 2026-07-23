$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\DynamicPlanSolveAgentRunService.java"
$content = Get-Content -Raw -LiteralPath $path

$old = "boolean wantsEdit = contains(prompt,"
$new = "boolean wantsEdit = !request.getImageUrls().isEmpty() && contains(prompt,"

if ($content.Contains($new)) {
    Write-Host "Step 23 image-edit routing change is already present."
    exit 0
}
if (($content.IndexOf($old)) -lt 0) {
    throw "Expected image-edit routing expression was not found. No files were changed."
}

$updated = $content.Replace($old, $new)
[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"
Write-Host "Step 23 image-edit routing change completed. Rebuild and restart the backend."
