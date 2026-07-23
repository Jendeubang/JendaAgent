$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/showcase.Dockerfile"
$source = [System.IO.File]::ReadAllText($path)
$old = "COPY --from=builder --chown=node:node /app/public ./public`n"
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 1) { throw "Expected exactly one public COPY instruction, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, ""), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
