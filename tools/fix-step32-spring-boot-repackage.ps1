$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "deploy/backend.Dockerfile"
$source = [System.IO.File]::ReadAllText($path)
$old = "RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package"
$new = "RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package spring-boot:repackage"
$count = [regex]::Matches($source, [regex]::Escape($old)).Count
if ($count -ne 1) { throw "Expected exactly one Maven package command, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Updated $path"
