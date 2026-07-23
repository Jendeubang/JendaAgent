$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) 'showcase\lib\cosDirectUpload.ts'
$content = [System.IO.File]::ReadAllText($path)
$pattern = '    if \(message\.includes\(": 401"\)\) throw new Error\(".*?"\);'
$count = ([regex]::Matches($content, $pattern)).Count
if ($count -ne 1) { throw "Expected exactly one upload 401 message in $path, found $count. No file was changed." }
$replacement = '    if (message.includes(": 401")) throw new Error("Login expired. Please sign in again before uploading.");'
$content = [regex]::Replace($content, $pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement })
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Corrected upload expiration message."
