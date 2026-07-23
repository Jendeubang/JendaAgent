$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Replace-OneRegex([string]$RelativePath, [string]$Pattern, [string]$Replacement) {
    $path = Join-Path $backend $RelativePath
    $source = [System.IO.File]::ReadAllText($path)
    $count = ([regex]::Matches($source, $Pattern)).Count
    if ($count -ne 1) { throw "Expected one matching method block in $path, found $count. No files were changed for this replacement." }
    $updated = [regex]::Replace($source, $Pattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $Replacement }, 1)
    [System.IO.File]::WriteAllText($path, $updated, $utf8)
    Write-Host "Updated $path"
}

$clientCatchPattern = '(?s)        \} catch \(IOException error\) \{\s*            throw new IllegalStateException\([^;]+;\s*        \}'
$clientCatchReplacement = @'
        } catch (Exception error) {
            // Planning and summary are advisory. A provider outage must not prevent tool execution or history persistence.
            return ModelCompletion.skipped("Model temporarily unavailable; using rule routing: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
'@.TrimEnd()
Replace-OneRegex "src/main/java/com/jd/genie/service/agent/OpenAiCompatibleChatClient.java" $clientCatchPattern $clientCatchReplacement

$routingPattern = '(?s)    private List<AgentToolType> selectTools\(AgentRunRequest request\) \{.*?^    \}(?=\s*^    private List<AgentToolResult> executeTools)'
$routingReplacement = @'
    private List<AgentToolType> selectTools(AgentRunRequest request) {
        String prompt = request.getPrompt().toLowerCase();
        boolean hasReferenceImage = !request.getImageUrls().isEmpty();
        boolean wantsGeneration = containsAny(prompt,
                "\u751f\u6210\u4e00\u5f20", "\u751f\u6210\u56fe\u7247", "\u751f\u6210\u56fe\u50cf", "\u7ed8\u5236", "\u753b\u4e00\u5f20", "\u521b\u5efa\u6d77\u62a5", "\u6587\u751f\u56fe", "\u751f\u6210", "\u52a8\u6f2b", "\u6d77\u62a5",
                "generate an image", "create an image", "draw an image");
        boolean wantsEdit = containsAny(prompt,
                "\u7f16\u8f91", "\u80cc\u666f", "\u98ce\u683c", "\u6e05\u6670", "\u4fee\u590d", "\u66ff\u6362", "\u5c40\u90e8", "edit", "background", "style");
        List<AgentToolType> selected = new ArrayList<>();
        if (hasReferenceImage && containsAny(prompt, "\u8bc6\u522b", "\u63d0\u53d6\u6587\u5b57", "\u56fe\u4e2d\u6587\u5b57", "ocr", "extract text", "read text")) {
            selected.add(AgentToolType.OCR);
        }
        if (wantsGeneration) {
            selected.add(AgentToolType.IMAGE_GENERATE);
        } else if (hasReferenceImage && wantsEdit) {
            selected.add(AgentToolType.IMAGE_EDIT);
        }
        return selected;
    }
'@.TrimEnd()
Replace-OneRegex "src/main/java/com/jd/genie/service/agent/ModelToolAgentRunService.java" $routingPattern $routingReplacement

$controllerPath = Join-Path $backend "src/main/java/com/jd/genie/controller/QwenImageEditToolGatewayController.java"
$controllerSource = [System.IO.File]::ReadAllText($controllerPath)
$controllerPattern = '(?s)(        if \(!"image_edit"\.equalsIgnoreCase\(request\.task_type\(\)\) \{\s*            throw new ResponseStatusException\(HttpStatus\.BAD_REQUEST, "task_type must be image_edit"\);\s*        \}\s*)(        QwenImageEditGatewayClient\.EditedImage result = client\.edit\(request\);)'
$matches = [regex]::Matches($controllerSource, $controllerPattern)
if ($matches.Count -ne 1) { throw "Expected one image-edit validation block in $controllerPath, found $($matches.Count)." }
$controllerUpdated = [regex]::Replace($controllerSource, $controllerPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $match.Groups[1].Value + "        if (request.image_urls() == null || request.image_urls().isEmpty()) {" + [Environment]::NewLine + "            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, \"image_edit requires at least one image_url\");" + [Environment]::NewLine + "        }" + [Environment]::NewLine + $match.Groups[2].Value }, 1)
[System.IO.File]::WriteAllText($controllerPath, $controllerUpdated, $utf8)
Write-Host "Updated $controllerPath"

Write-Host "Step 29 v3 completed. Rebuild and restart the backend."
