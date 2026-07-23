$path = Join-Path $PSScriptRoot "apply-step40-tool-catalog-and-remaining-workbenches.ps1"
$source = [System.IO.File]::ReadAllText($path)
$source = $source.Replace('required: $false', 'required: false')
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Output "Fixed TypeScript boolean literals in Step 40 script."
