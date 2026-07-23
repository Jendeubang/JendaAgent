$ErrorActionPreference = "Stop"

function Assert-Once {
    param([pscustomobject]$Replacement)
    $content = [System.IO.File]::ReadAllText($Replacement.Path)
    $first = $content.IndexOf($Replacement.Old, [System.StringComparison]::Ordinal)
    $second = if ($first -ge 0) { $content.IndexOf($Replacement.Old, $first + $Replacement.Old.Length, [System.StringComparison]::Ordinal) } else { -1 }
    if ($first -lt 0 -or $second -ge 0) {
        throw "Expected exactly one matching block in $($Replacement.Path). No source files were changed."
    }
}

function Apply-Replacement {
    param([pscustomobject]$Replacement)
    $content = [System.IO.File]::ReadAllText($Replacement.Path)
    $index = $content.IndexOf($Replacement.Old, [System.StringComparison]::Ordinal)
    $updated = $content.Substring(0, $index) + $Replacement.New + $content.Substring($index + $Replacement.Old.Length)
    [System.IO.File]::WriteAllText($Replacement.Path, $updated, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Updated $($Replacement.Path)"
}

$root = Split-Path -Parent $PSScriptRoot
$history = Join-Path $root "genie-backend/src/main/java/com/jd/genie/service/agent/AgentHistoryStore.java"
$runtime = Join-Path $root "genie-backend/src/main/java/com/jd/genie/service/agent/ModelToolAgentRunService.java"
$replacements = @(
    [pscustomobject]@{ Path = Join-Path $root "genie-backend/src/main/java/com/jd/genie/model/agent/AgentEventType.java"; Old = @'
    TASK("task"),
    TOOL_RESULT("tool_result"),
'@; New = @'
    TASK("task"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
'@ },
    [pscustomobject]@{ Path = $history; Old = @'
        execute("CREATE TABLE IF NOT EXISTS agent_task_message (event_id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64), title VARCHAR(255), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_result_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, result_json TEXT)");
'@; New = @'
        execute("CREATE TABLE IF NOT EXISTS agent_task_message (event_id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64), title VARCHAR(255), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_call_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, request_json TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_result_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, result_json TEXT)");
'@ },
    [pscustomobject]@{ Path = $history; Old = @'
            case TASK -> jdbcTemplate.update("INSERT INTO agent_task_message (event_id, task_id, title, content) VALUES (?, ?, ?, ?)",
                    event.eventId(), text(payload, "taskId"), text(payload, "title"), text(payload, "content"));
            case TOOL_RESULT -> jdbcTemplate.update("INSERT INTO agent_tool_result_message (event_id, tool_name, title, content, result_json) VALUES (?, ?, ?, ?, ?)",
'@; New = @'
            case TASK -> jdbcTemplate.update("INSERT INTO agent_task_message (event_id, task_id, title, content) VALUES (?, ?, ?, ?)",
                    event.eventId(), text(payload, "taskId"), text(payload, "title"), text(payload, "content"));
            case TOOL_CALL -> jdbcTemplate.update("INSERT INTO agent_tool_call_message (event_id, tool_name, title, content, request_json) VALUES (?, ?, ?, ?, ?)",
                    event.eventId(), text(payload, "tool"), text(payload, "title"), text(payload, "content"), toJson(payload));
            case TOOL_RESULT -> jdbcTemplate.update("INSERT INTO agent_tool_result_message (event_id, tool_name, title, content, result_json) VALUES (?, ?, ?, ?, ?)",
'@ },
    [pscustomobject]@{ Path = $history; Old = @'
            case "task" -> AgentEventType.TASK;
            case "tool_result" -> AgentEventType.TOOL_RESULT;
'@; New = @'
            case "task" -> AgentEventType.TASK;
            case "tool_call" -> AgentEventType.TOOL_CALL;
            case "tool_result" -> AgentEventType.TOOL_RESULT;
'@ },
    [pscustomobject]@{ Path = $runtime; Old = @'
            }

            List<AgentToolResult> toolResults = executeTools(tools, request);
'@; New = @'
            }
            for (AgentToolType tool : tools) {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING,
                        "ToolRouter", Map.of(
                        "tool", tool.name(),
                        "title", "ToolRouter: " + tool.getDisplayName() + " 调用中",
                        "content", "已通过统一工具协议发起 " + tool.getDisplayName() + " 调用。")));
            }

            List<AgentToolResult> toolResults = executeTools(tools, request);
'@ },
    [pscustomobject]@{ Path = $runtime; Old = @'
        } else if (containsAny(prompt, "生成", "绘制", "海报", "图片", "图像", "generate", "create", "image")) {
            selected.add(AgentToolType.IMAGE_GENERATE);
'@; New = @'
        } else if (containsAny(prompt, "生成一张", "生成图片", "生成图像", "绘制", "画一张", "创建海报", "文生图",
                "generate an image", "create an image", "draw an image")) {
            selected.add(AgentToolType.IMAGE_GENERATE);
'@ }
)

# Validate all source locations first. A failed preflight makes no source changes.
foreach ($replacement in $replacements) { Assert-Once $replacement }
foreach ($replacement in $replacements) { Apply-Replacement $replacement }
Write-Host "Step 14 backend changes completed. Rebuild and restart the backend."
