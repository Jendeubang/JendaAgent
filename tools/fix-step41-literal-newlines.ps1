$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) 'showcase\app\zh\agent\page.tsx'
$content = [System.IO.File]::ReadAllText($path)
$count = ([regex]::Matches($content, '`r`n')).Count
if ($count -ne 2) { throw "Expected exactly 2 literal newline tokens in $path, found $count. No file was changed." }
$content = $content.Replace('`r`n', [Environment]::NewLine)
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Replaced literal newline tokens in $path"
