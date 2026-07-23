$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$showcase = Join-Path $root "showcase"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Replace-Text([string]$RelativePath, [string]$Old, [string]$New) {
    $path = Join-Path $showcase $RelativePath
    $source = [System.IO.File]::ReadAllText($path)
    if (-not $source.Contains($Old)) { throw "Expected text was not found in $path. No files were changed." }
    [System.IO.File]::WriteAllText($path, $source.Replace($Old, $New), $utf8)
    Write-Host "Updated $path"
}

# First apply the upload implementation from the stable template. Its remaining replacements are corrected below.
$template = Join-Path $PSScriptRoot "apply-step27-mysql-jwt-frontend.ps1"
$source = [System.IO.File]::ReadAllText($template)
$source = [regex]::Replace($source, '(?m)^Replace-Text "app/(agent-studio|image-studio)/page\.tsx".*\r?\n', '')
$generated = Join-Path $PSScriptRoot "apply-step27-mysql-jwt-frontend-v2.generated.ps1"
[System.IO.File]::WriteAllText($generated, $source, $utf8)
try { & $generated } finally { Remove-Item -LiteralPath $generated -Force -ErrorAction SilentlyContinue }

$newline = [Environment]::NewLine
$uploadImport = 'import { uploadImageDirect, type DirectUploadedAsset } from "../../lib/cosDirectUpload";'
$authImport = $uploadImport + $newline + 'import { agentFetch } from "../../lib/agentAuth";'
Replace-Text "app/agent-studio/page.tsx" $uploadImport $authImport
Replace-Text "app/agent-studio/page.tsx" 'fetch(`${apiBaseUrl}/api/v1/agent' 'agentFetch(`${apiBaseUrl}/api/v1/agent'
Replace-Text "app/image-studio/page.tsx" $uploadImport $authImport
Replace-Text "app/image-studio/page.tsx" 'fetch(`${apiBaseUrl}/api/v1/agent' 'agentFetch(`${apiBaseUrl}/api/v1/agent'
Replace-Text "app/image-studio/page.tsx" 'fetch(`${apiBaseUrl}/api/v2/agent' 'agentFetch(`${apiBaseUrl}/api/v2/agent'
Write-Host "Step 27 frontend changes completed. Restart the Next.js development server."
