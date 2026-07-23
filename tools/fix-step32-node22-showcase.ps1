$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/showcase.Dockerfile"
$source = [System.IO.File]::ReadAllText($path)
$old = "node:20-bookworm-slim"
$new = "node:22-bookworm-slim"
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 2) { throw "Expected exactly two Node 20 image references, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
