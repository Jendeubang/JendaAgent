$path = Join-Path $PSScriptRoot "..\showcase\app\zh\tool\[slug]\page.tsx"
$source = [System.IO.File]::ReadAllText($path)
$source = $source.Replace(';`n  if (icon === "character")', ";`r`n  if (icon === \"character\")")
$source = $source.Replace(';`n  if (icon === "poster")', ";`r`n  if (icon === \"poster\")")
$source = $source.Replace(';`n  if (icon === "emoji")', ";`r`n  if (icon === \"emoji\")")
$source = $source.Replace(';`n  if (icon === "detail")', ";`r`n  if (icon === \"detail\")")
$source = $source.Replace('as CSSProperties;`n  const categoryLabel', "as CSSProperties;`r`n  const categoryLabel")
$source = $source.Replace('copy.category;`n  const uploadLabel', "copy.category;`r`n  const uploadLabel")
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Output "Fixed Step 40 literal newline markers."
