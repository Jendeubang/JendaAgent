$path = Join-Path $PSScriptRoot "..\showcase\app\zh\tool\[slug]\page.tsx"
$source = [System.IO.File]::ReadAllText($path)
$source = $source.Replace('`n', [Environment]::NewLine)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Output "Replaced literal newline markers in the TypeScript source."
