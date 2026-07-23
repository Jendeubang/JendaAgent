$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$template = Join-Path $PSScriptRoot "apply-step28-user-experience-loop.ps1"

# The base script intentionally keeps all source replacements ASCII-safe. Convert its literal newline markers
# after execution so PowerShell does not inject backticks into Java or TypeScript source.
& $template

$utf8 = [System.Text.UTF8Encoding]::new($false)
$paths = @(
    "genie-backend\src\main\java\com\jd\genie\service\agent\StsDirectUploadTicketService.java",
    "genie-backend\src\main\java\com\jd\genie\service\agent\DynamicPlanSolveAgentRunService.java",
    "showcase\app\agent-studio\page.tsx",
    "showcase\app\image-studio\page.tsx"
)
foreach ($relativePath in $paths) {
    $path = Join-Path $root $relativePath
    $source = [System.IO.File]::ReadAllText($path)
    $fixed = $source.Replace('`r`n', [Environment]::NewLine).Replace('`n', [Environment]::NewLine)
    if ($source -ne $fixed) {
        [System.IO.File]::WriteAllText($path, $fixed, $utf8)
        Write-Host "Fixed newline markers in $path"
    }
}
Write-Host "Step 28 v2 completed. Compile the backend before starting it."
