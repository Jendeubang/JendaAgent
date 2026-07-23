$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) "genie-backend/src/main/java/com/jd/genie/service/agent/PersistentAgentWorkspaceCatalog.java"
$content = [System.IO.File]::ReadAllText($path)
$old = "sessions.getFirst()"
$count = ([regex]::Matches($content, [regex]::Escape($old))).Count
if ($count -ne 1) { throw "Expected one Java 21 List.getFirst() call, found $count. No file was changed." }
$updated = $content.Replace($old, "sessions.get(0)")
[System.IO.File]::WriteAllText($path, $updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Replaced List.getFirst() with Java 17-compatible get(0)."
