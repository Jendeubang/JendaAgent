$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/showcase.Dockerfile"
$source = [System.IO.File]::ReadAllText($path)
$old = "COPY package.json pnpm-lock.yaml ./"
$new = "COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./"
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 1) { throw "Expected exactly one dependency COPY line, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
