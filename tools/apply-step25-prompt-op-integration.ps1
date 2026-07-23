$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$eventTypePath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\model\agent\AgentEventType.java"
$historyPath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\AgentHistoryStore.java"
$dynamicPath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\DynamicPlanSolveAgentRunService.java"

$eventType = [System.IO.File]::ReadAllText($eventTypePath)
$history = [System.IO.File]::ReadAllText($historyPath)
$dynamic = [System.IO.File]::ReadAllText($dynamicPath)

if ($eventType.Contains('PROMPT_OPTIMIZATION("prompt_optimization")')) {
    Write-Host "Step 25 PromptOp integration is already present."
    exit 0
}

function Require-One([string]$Content, [string]$Needle, [string]$Name) {
    $count = ([regex]::Matches($Content, [regex]::Escape($Needle))).Count
    if ($count -ne 1) {
        throw "Expected exactly one $Name block, found $count. No files were changed."
    }
}

$eventAnchor = '    PLAN("plan"),' 
$historySchemaAnchor = '        execute("CREATE TABLE IF NOT EXISTS agent_plan_message (event_id VARCHAR(64) PRIMARY KEY, title VARCHAR(255), content TEXT, steps_json TEXT)");'
$historyRouteAnchor = @'
            case PLAN -> jdbcTemplate.update("INSERT INTO agent_plan_message (event_id, title, content, steps_json) VALUES (?, ?, ?, ?)",
                    event.eventId(), text(payload, "title"), text(payload, "content"), toJson(payload.get("steps")));
'@.TrimStart("`n")
$historyParseAnchor = '            case "plan" -> AgentEventType.PLAN;'
$dynamicFieldAnchor = '    private final OpenAiCompatibleChatClient chatClient;'
$dynamicStateAnchor = '        List<AgentToolType> tools = selectInitialTools(request);'
$dynamicOptimizationAnchor = @'
                if (tools.isEmpty()) {
                    memory.add("Round " + round + " has no applicable tool; continue to delivery summary.");
                    break;
                }

'@.TrimStart("`n")
$dynamicExecuteAnchor = '                List<AgentToolResult> roundResults = executeTools(tools, request);'
$dynamicMethodAnchor = @'
    private List<AgentToolResult> executeTools(List<AgentToolType> tools, AgentRunRequest request) {
        List<CompletableFuture<AgentToolResult>> futures = tools.stream()
                .map(tool -> CompletableFuture.supplyAsync(
                        () -> toolClient.execute(tool, request.getPrompt(), request.getImageUrls())))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }
'@.TrimStart("`n")

Require-One $eventType $eventAnchor "AgentEventType"
Require-One $history $historySchemaAnchor "history schema"
Require-One $history $historyRouteAnchor "history route"
Require-One $history $historyParseAnchor "history parser"
Require-One $dynamic $dynamicFieldAnchor "dynamic service field"
Require-One $dynamic $dynamicStateAnchor "dynamic service state"
Require-One $dynamic $dynamicOptimizationAnchor "dynamic optimization insertion"
Require-One $dynamic $dynamicExecuteAnchor "dynamic tool execution"
Require-One $dynamic $dynamicMethodAnchor "dynamic executeTools method"

$eventType = $eventType.Replace($eventAnchor, $eventAnchor + [Environment]::NewLine + '    PROMPT_OPTIMIZATION("prompt_optimization"),')
$history = $history.Replace($historySchemaAnchor, $historySchemaAnchor + [Environment]::NewLine + '        execute("CREATE TABLE IF NOT EXISTS agent_prompt_optimization_message (event_id VARCHAR(64) PRIMARY KEY, original_prompt TEXT, optimized_prompt TEXT, retrieved_rules_json TEXT, provider VARCHAR(64), content TEXT)");')
$history = $history.Replace($historyRouteAnchor, $historyRouteAnchor + @'
            case PROMPT_OPTIMIZATION -> jdbcTemplate.update("INSERT INTO agent_prompt_optimization_message (event_id, original_prompt, optimized_prompt, retrieved_rules_json, provider, content) VALUES (?, ?, ?, ?, ?, ?)",
                    event.eventId(), text(payload, "originalPrompt"), text(payload, "optimizedPrompt"), toJson(payload.get("retrievedRules")), text(payload, "provider"), text(payload, "content"));
'@)
$history = $history.Replace($historyParseAnchor, $historyParseAnchor + [Environment]::NewLine + '            case "prompt_optimization" -> AgentEventType.PROMPT_OPTIMIZATION;')

$dynamic = $dynamic.Replace($dynamicFieldAnchor, $dynamicFieldAnchor + [Environment]::NewLine + '    private final PromptOpAgent promptOpAgent;')
$dynamic = $dynamic.Replace($dynamicStateAnchor, $dynamicStateAnchor + [Environment]::NewLine + '        String executionPrompt = request.getPrompt();' + [Environment]::NewLine + '        boolean promptOptimized = false;')
$optimizationBlock = @'
                if (!promptOptimized && hasImageTool(tools)) {
                    PromptOptimization optimization = promptOpAgent.optimize(request.getPrompt(), tools, request.getImageUrls());
                    promptOptimized = true;
                    executionPrompt = optimization.optimizedPrompt();
                    publish(emitter, event(sessionId, runId, sequence, AgentEventType.PROMPT_OPTIMIZATION, AgentEventStatus.COMPLETE,
                            "PromptOpAgent", Map.of(
                            "title", "PromptOpAgent: prompt optimization",
                            "content", optimization.detail(),
                            "originalPrompt", optimization.originalPrompt(),
                            "optimizedPrompt", optimization.optimizedPrompt(),
                            "retrievedRules", optimization.retrievedRules(),
                            "toolTypes", tools.stream().map(AgentToolType::name).toList(),
                            "applied", optimization.applied(),
                            "provider", optimization.provider(),
                            "round", round)));
                    memory.add("PromptOpAgent rules: " + (optimization.retrievedRules().isEmpty() ? "none" : String.join(", ", optimization.retrievedRules())));
                }

'@
$dynamic = $dynamic.Replace($dynamicOptimizationAnchor, $dynamicOptimizationAnchor + $optimizationBlock)
$dynamic = $dynamic.Replace($dynamicExecuteAnchor, '                List<AgentToolResult> roundResults = executeTools(tools, executionPrompt, request.getImageUrls());')
$newMethod = @'
    private boolean hasImageTool(List<AgentToolType> tools) {
        return tools.contains(AgentToolType.IMAGE_GENERATE) || tools.contains(AgentToolType.IMAGE_EDIT);
    }

    private List<AgentToolResult> executeTools(List<AgentToolType> tools, String prompt, List<String> imageUrls) {
        List<CompletableFuture<AgentToolResult>> futures = tools.stream()
                .map(tool -> CompletableFuture.supplyAsync(
                        () -> toolClient.execute(tool, prompt, imageUrls)))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }
'@.TrimStart("`n")
$dynamic = $dynamic.Replace($dynamicMethodAnchor, $newMethod)

$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($eventTypePath, $eventType, $utf8)
[System.IO.File]::WriteAllText($historyPath, $history, $utf8)
[System.IO.File]::WriteAllText($dynamicPath, $dynamic, $utf8)
Write-Host "Updated $eventTypePath"
Write-Host "Updated $historyPath"
Write-Host "Updated $dynamicPath"
Write-Host "Step 25 PromptOpAgent integration completed. Rebuild and restart the backend."
