$path = Join-Path $PSScriptRoot "..\showcase\app\zh\agent\page.tsx"
$source = [System.IO.File]::ReadAllText($path)
$pattern = '<h1><span>{copy.lineOne}</span><span>{copy.lineTwo}.*?</span></h1>'
$replacement = '<h1><span>{copy.lineOne}</span><span>{copy.lineTwo}<em>.</em></span></h1>'
$updated = [System.Text.RegularExpressions.Regex]::Replace($source, $pattern, $replacement, [System.Text.RegularExpressions.RegexOptions]::Singleline)
if ($updated -eq $source) { throw "Expected hero heading markup was not found." }
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $updated, $utf8)
Write-Output "Fixed hero heading markup."
