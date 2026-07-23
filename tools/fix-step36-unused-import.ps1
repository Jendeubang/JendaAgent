$path = Join-Path $PSScriptRoot "..\showcase\app\zh\agent\page.tsx"
$source = [System.IO.File]::ReadAllText($path)
$old = 'import { Button, Tag } from "antd";'
$new = 'import { Button } from "antd";'
if (($source.Split($old).Length - 1) -ne 1) { throw "Expected exactly one unused Tag import." }
$source = $source.Replace($old, $new)
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Output "Removed unused Tag import."
