$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) 'showcase\lib\cosDirectUpload.ts'
$content = [System.IO.File]::ReadAllText($path)
$old = '"\\\\u767b\\\\u5f55\\\\u5df2\\\\u8fc7\\\\u671f\\\\uff0c\\\\u8bf7\\\\u91cd\\\\u65b0\\\\u767b\\\\u5f55\\\\u540e\\\\u4e0a\\\\u4f20\\\\u56fe\\\\u7247"'
$new = '"\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55\u540e\u4e0a\u4f20\u56fe\u7247"'
$count = ([regex]::Matches($content, [regex]::Escape($old))).Count
if ($count -ne 1) { throw "Expected exactly one double-escaped upload message in $path, found $count. No file was changed." }
$content = $content.Replace($old, $new)
[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host "Corrected upload expiration message escape sequence."
