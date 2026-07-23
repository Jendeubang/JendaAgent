$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) "genie-backend/src/main/java/com/jd/genie/service/agent/DynamicPlanSolveAgentRunService.java"
$content = [System.IO.File]::ReadAllText($path)
$old = @'
                if (tools.isEmpty()) {
                    memory.add("失败工具未实际调用或未配置，不重试，改为交付可用结果。");
                    break;
                }
'@
$new = @'
                if (tools.isEmpty()) {
                    memory.add("失败工具未实际调用或未配置，不重试；下一轮仅修订交付计划。");
                    if (round < MAX_ROUNDS) {
                        continue;
                    }
                    break;
                }
'@
if ($content.Contains("`r`n")) { $old = $old -replace "`r?`n", "`r`n"; $new = $new -replace "`r?`n", "`r`n" }
$count = ([regex]::Matches($content, [regex]::Escape($old))).Count
if ($count -ne 1) { throw "Expected one unavailable-tool replan block, found $count. No file was changed." }
[System.IO.File]::WriteAllText($path, $content.Replace($old, $new), (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Unavailable tools now trigger one safe replan round instead of a blind retry."
