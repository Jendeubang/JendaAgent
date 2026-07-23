$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Replace-Exactly([string]$RelativePath, [string]$Old, [string]$New) {
    $path = Join-Path $backend $RelativePath
    $source = [System.IO.File]::ReadAllText($path)
    $count = ([regex]::Matches($source, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "Expected exactly one matching block in $path, found $count. No files were changed for this replacement." }
    [System.IO.File]::WriteAllText($path, $source.Replace($Old, $New), $utf8)
    Write-Host "Updated $path"
}

$oldCatch = @'
        } catch (IOException error) {
            throw new IllegalStateException("模型请求失败: " + error.getMessage(), error);
        }
'@
$newCatch = @'
        } catch (Exception error) {
            // Planning and summary are advisory. A provider outage must not prevent tool execution or history persistence.
            return ModelCompletion.skipped("模型暂不可用，已降级为规则编排: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
'@
Replace-Exactly "src/main/java/com/jd/genie/service/agent/OpenAiCompatibleChatClient.java" $oldCatch $newCatch

$oldRouting = @'
    private List<AgentToolType> selectTools(AgentRunRequest request) {
        String prompt = request.getPrompt().toLowerCase();
        List<AgentToolType> selected = new ArrayList<>();
        if (!request.getImageUrls().isEmpty()) {
            selected.add(AgentToolType.OCR);
        }
        if (containsAny(prompt, "编辑", "背景", "风格", "清晰", "修复", "替换", "局部", "edit", "background", "style")) {
            selected.add(AgentToolType.IMAGE_EDIT);
        } else if (containsAny(prompt, "生成一张", "生成图片", "生成图像", "绘制", "画一张", "创建海报", "文生图",
                "generate an image", "create an image", "draw an image")) {
            selected.add(AgentToolType.IMAGE_GENERATE);
        }
        return selected;
    }
'@
$newRouting = @'
    private List<AgentToolType> selectTools(AgentRunRequest request) {
        String prompt = request.getPrompt().toLowerCase();
        boolean hasReferenceImage = !request.getImageUrls().isEmpty();
        boolean wantsGeneration = containsAny(prompt, "生成一张", "生成图片", "生成图像", "绘制", "画一张", "创建海报", "文生图",
                "生成", "动漫", "海报", "generate an image", "create an image", "draw an image");
        boolean wantsEdit = containsAny(prompt, "编辑", "背景", "风格", "清晰", "修复", "替换", "局部", "edit", "background", "style");
        List<AgentToolType> selected = new ArrayList<>();
        if (hasReferenceImage && containsAny(prompt, "识别", "提取文字", "图中文字", "ocr", "extract text", "read text")) {
            selected.add(AgentToolType.OCR);
        }
        if (wantsGeneration) {
            selected.add(AgentToolType.IMAGE_GENERATE);
        } else if (hasReferenceImage && wantsEdit) {
            selected.add(AgentToolType.IMAGE_EDIT);
        }
        return selected;
    }
'@
Replace-Exactly "src/main/java/com/jd/genie/service/agent/ModelToolAgentRunService.java" $oldRouting $newRouting

$oldValidation = @'
        if (!"image_edit".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be image_edit");
        }
        QwenImageEditGatewayClient.EditedImage result = client.edit(request);
'@
$newValidation = @'
        if (!"image_edit".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be image_edit");
        }
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_edit requires at least one image_url");
        }
        QwenImageEditGatewayClient.EditedImage result = client.edit(request);
'@
Replace-Exactly "src/main/java/com/jd/genie/controller/QwenImageEditToolGatewayController.java" $oldValidation $newValidation

Write-Host "Step 29 model resilience and routing fix completed. Rebuild and restart the backend."
