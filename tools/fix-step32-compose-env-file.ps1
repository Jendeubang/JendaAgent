$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/docker-compose.yml"
$source = [System.IO.File]::ReadAllText($path)
$old = "    env_file: .env"
$new = "    env_file:`n      - `${ENV_FILE:-.env}"
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 1) { throw "Expected exactly one backend env_file entry, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
