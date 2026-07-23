$ErrorActionPreference = "Stop"
$templatePath = Join-Path $PSScriptRoot "fix-step29-model-resilience-and-image-routing-v5.ps1"
$template = [System.IO.File]::ReadAllText($templatePath)
$old = '$routingPattern = ''(?s)    private List<AgentToolType> selectTools\(AgentRunRequest request\) \{.*?^    \}(?=\s*^    private List<AgentToolResult> executeTools)'''
$new = '$routingPattern = ''(?sm)    private List<AgentToolType> selectTools\(AgentRunRequest request\) \{.*?^    \}(?=\s*^    private List<AgentToolResult> executeTools)'''
if (-not $template.Contains($old)) { throw "Step 29 v5 routing pattern was not found. No files were changed." }
$template = $template.Replace($old, $new)
$generated = Join-Path $PSScriptRoot "fix-step29-model-resilience-and-image-routing-v7.generated.ps1"
[System.IO.File]::WriteAllText($generated, $template, [System.Text.UTF8Encoding]::new($false))
try { & $generated } finally { Remove-Item -LiteralPath $generated -Force -ErrorAction SilentlyContinue }
