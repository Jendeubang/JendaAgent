$ErrorActionPreference = "Stop"

function Replace-Once {
    param(
        [string]$Path,
        [string]$OldValue,
        [string]$NewValue
    )

    $content = [System.IO.File]::ReadAllText($Path)
    $first = $content.IndexOf($OldValue, [System.StringComparison]::Ordinal)
    $second = if ($first -ge 0) { $content.IndexOf($OldValue, $first + $OldValue.Length, [System.StringComparison]::Ordinal) } else { -1 }
    if ($first -lt 0 -or $second -ge 0) {
        throw "Expected exactly one matching block in $Path. No files were changed after this failure."
    }

    $updated = $content.Substring(0, $first) + $NewValue + $content.Substring($first + $OldValue.Length)
    [System.IO.File]::WriteAllText($Path, $updated, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Updated $Path"
}

$root = Split-Path -Parent $PSScriptRoot

Replace-Once (Join-Path $root "genie-backend/src/main/java/com/jd/genie/model/agent/AgentEventType.java") @'
    TASK("task"),
    TOOL_RESULT("tool_result"),
'@ @'
    TASK("task"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
'@

$history = Join-Path $root "genie-backend/src/main/java/com/jd/genie/service/agent/AgentHistoryStore.java"
Replace-Once $history @'
        execute("CREATE TABLE IF NOT EXISTS agent_task_message (event_id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64), title VARCHAR(255), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_result_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, result_json TEXT)");
'@ @'
        execute("CREATE TABLE IF NOT EXISTS agent_task_message (event_id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64), title VARCHAR(255), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_call_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, request_json TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_result_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, result_json TEXT)");
'@
Replace-Once $history @'
            case TASK -> jdbcTemplate.update("INSERT INTO agent_task_message (event_id, task_id, title, content) VALUES (?, ?, ?, ?)",
                    event.eventId(), text(payload, "taskId"), text(payload, "title"), text(payload, "content"));
            case TOOL_RESULT -> jdbcTemplate.update("INSERT INTO agent_tool_result_message (event_id, tool_name, title, content, result_json) VALUES (?, ?, ?, ?, ?)",
'@ @'
            case TASK -> jdbcTemplate.update("INSERT INTO agent_task_message (event_id, task_id, title, content) VALUES (?, ?, ?, ?)",
                    event.eventId(), text(payload, "taskId"), text(payload, "title"), text(payload, "content"));
            case TOOL_CALL -> jdbcTemplate.update("INSERT INTO agent_tool_call_message (event_id, tool_name, title, content, request_json) VALUES (?, ?, ?, ?, ?)",
                    event.eventId(), text(payload, "tool"), text(payload, "title"), text(payload, "content"), toJson(payload));
            case TOOL_RESULT -> jdbcTemplate.update("INSERT INTO agent_tool_result_message (event_id, tool_name, title, content, result_json) VALUES (?, ?, ?, ?, ?)",
'@
Replace-Once $history @'
            case "task" -> AgentEventType.TASK;
            case "tool_result" -> AgentEventType.TOOL_RESULT;
'@ @'
            case "task" -> AgentEventType.TASK;
            case "tool_call" -> AgentEventType.TOOL_CALL;
            case "tool_result" -> AgentEventType.TOOL_RESULT;
'@

$runtime = Join-Path $root "genie-backend/src/main/java/com/jd/genie/service/agent/ModelToolAgentRunService.java"
Replace-Once $runtime @'
            }

            List<AgentToolResult> toolResults = executeTools(tools, request);
'@ @'
            }
            for (AgentToolType tool : tools) {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING,
                        "ToolRouter", Map.of(
                        "tool", tool.name(),
                        "title", "ToolRouter: " + tool.getDisplayName() + " 调用中",
                        "content", "已通过统一工具协议发起 " + tool.getDisplayName() + " 调用。")));
            }

            List<AgentToolResult> toolResults = executeTools(tools, request);
'@
Replace-Once $runtime @'
        } else if (containsAny(prompt, "生成", "绘制", "海报", "图片", "图像", "generate", "create", "image")) {
            selected.add(AgentToolType.IMAGE_GENERATE);
'@ @'
        } else if (containsAny(prompt, "生成一张", "生成图片", "生成图像", "绘制", "画一张", "创建海报", "文生图",
                "generate an image", "create an image", "draw an image")) {
            selected.add(AgentToolType.IMAGE_GENERATE);
'@

Replace-Once (Join-Path $root "showcase/app/live-multimodal/page.tsx") @'
      task: "子任务执行",
      tool_result: "工具结果",
'@ @'
      task: "子任务执行",
      tool_call: "工具调用",
      tool_result: "工具结果",
'@

Write-Host "Step 14 completed. Rebuild and restart the backend."
