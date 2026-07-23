$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/showcase.Dockerfile"
$source = [System.IO.File]::ReadAllText($path)
$old = "corepack prepare pnpm@9.15.4 --activate"
$new = "corepack prepare pnpm@11.14.0 --activate"
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 1) { throw "Expected exactly one pnpm 9 activation line, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
