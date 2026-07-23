$path = Join-Path $PSScriptRoot "..\showcase\app\zh\agent\page.tsx"
$source = [System.IO.File]::ReadAllText($path)
$old = 'import { Button, Tag } from "antd";'
if ($source.IndexOf($old, [System.StringComparison]::Ordinal) -lt 0) { throw "Tag import was not found." }
$source = $source.Replace($old, 'import { Button } from "antd";')
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Output "Removed unused Tag import."
