$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) "genie-backend/src/main/java/com/jd/genie/service/agent/DynamicPlanSolveAgentRunService.java"
$content = [System.IO.File]::ReadAllText($path)
$pattern = 'memory\.add\("失败工具未实际调用或未配置，不重试，改为交付可用结果。"\);\s*break;'
$count = ([regex]::Matches($content, $pattern)).Count
if ($count -ne 1) { throw "Expected one unavailable-tool branch, found $count. No file was changed." }
$replacement = @'
memory.add("失败工具未实际调用或未配置，不重试；下一轮仅修订交付计划。");
                    if (round < MAX_ROUNDS) {
                        continue;
                    }
                    break;
'@
$updated = [regex]::Replace($content, $pattern, $replacement, 1)
[System.IO.File]::WriteAllText($path, $updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Unavailable tools now trigger one safe replan round instead of a blind retry."
