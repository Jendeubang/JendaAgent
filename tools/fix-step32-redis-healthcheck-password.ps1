$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/docker-compose.yml"
$source = [System.IO.File]::ReadAllText($path)
$old = @'
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:?Set REDIS_PASSWORD in deploy/.env}
    volumes:
'@
$new = @'
    command: redis-server --appendonly yes --requirepass ${REDIS_PASSWORD:?Set REDIS_PASSWORD in deploy/.env}
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:?Set REDIS_PASSWORD in deploy/.env}
    volumes:
'@
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 1) { throw "Expected exactly one Redis command block, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
