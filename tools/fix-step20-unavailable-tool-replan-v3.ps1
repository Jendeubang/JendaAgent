$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) "genie-backend/src/main/java/com/jd/genie/service/agent/DynamicPlanSolveAgentRunService.java"
$content = [System.IO.File]::ReadAllText($path)
$pattern = 'memory\.add\([^;]+\);\s*break;(?=\s*}\s*if \(round == MAX_ROUNDS\))'
$count = ([regex]::Matches($content, $pattern)).Count
if ($count -ne 1) { throw "Expected one unavailable-tool branch, found $count. No file was changed." }
$replacement = @'
memory.add("Unavailable tool was not retried; the next round revises the delivery plan.");
                    if (round < MAX_ROUNDS) {
                        continue;
                    }
                    break;
'@
$updated = [regex]::Replace($content, $pattern, $replacement, 1)
[System.IO.File]::WriteAllText($path, $updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Applied safe unavailable-tool replan branch."
