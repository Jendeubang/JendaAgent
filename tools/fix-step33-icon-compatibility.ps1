$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "showcase\app\agent-studio\page.tsx"
$utf8 = New-Object System.Text.UTF8Encoding($false)
$source = [System.IO.File]::ReadAllText($path)
$source = $source.Replace("SendOutlined, SparklesOutlined,", "SendOutlined, ThunderboltOutlined,")
$source = $source.Replace("<SparklesOutlined />", "<ThunderboltOutlined />")
if ($source.Contains("SparklesOutlined")) { throw "SparklesOutlined was not fully replaced." }
[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Host "Replaced unavailable SparklesOutlined with ThunderboltOutlined."
