$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "showcase\lib\cosDirectUpload.ts"
$source = [System.IO.File]::ReadAllText($path)
$old = '  if (!response.ok) throw new Error(`Server upload fallback failed: ${response.status}`);'
$new = @'
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`Server upload fallback failed: ${response.status}${detail ? ` - ${detail}` : ""}`);
  }
'@.TrimEnd()
if (-not $source.Contains($old)) {
    throw "Expected fallback error block was not found. No files were changed."
}
[System.IO.File]::WriteAllText($path, $source.Replace($old, $new), [System.Text.UTF8Encoding]::new($false))
Write-Host "Enabled detailed server upload fallback errors. Restart the frontend and retry once."
