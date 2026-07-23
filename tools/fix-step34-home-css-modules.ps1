$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "showcase\app\zh\page.module.css"
$text = [System.IO.File]::ReadAllText($path)
$text = $text.Replace("}footer{", "}.page footer{")
$text = $text.Replace("}footer b{", "}.page footer b{")
$text = $text.Replace("}footer span{", "}.page footer span{")
[System.IO.File]::WriteAllText($path, $text, [System.Text.UTF8Encoding]::new($false))
Write-Output "Fixed Step 34 CSS module footer selectors."
