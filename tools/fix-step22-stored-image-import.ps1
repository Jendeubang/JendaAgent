$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\GeneratedImageCosArchiver.java"
$content = Get-Content -Raw -LiteralPath $path
$import = "import com.jd.genie.model.agent.StoredAgentImage;"

if ($content.Contains($import)) {
    Write-Host "StoredAgentImage import is already present."
    exit 0
}

$anchor = "package com.jd.genie.service.agent;"
if (($content.IndexOf($anchor)) -lt 0) {
    throw "Expected package declaration was not found. No files were changed."
}

$updated = $content.Replace($anchor, $anchor + [Environment]::NewLine + [Environment]::NewLine + $import)
[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"
Write-Host "Step 22 StoredAgentImage import fix completed. Rebuild the backend."
