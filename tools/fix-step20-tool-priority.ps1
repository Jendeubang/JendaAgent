$ErrorActionPreference = "Stop"
$path = Join-Path (Split-Path -Parent $PSScriptRoot) "genie-backend/src/main/java/com/jd/genie/service/agent/DynamicPlanSolveAgentRunService.java"
$content = [System.IO.File]::ReadAllText($path)
$pattern = '(?s)    private List<AgentToolType> selectInitialTools\(AgentRunRequest request\) \{.*?\n    \}\n\n    private List<AgentToolResult> executeTools'
$count = ([regex]::Matches($content, $pattern)).Count
if ($count -ne 1) { throw "Expected one dynamic tool-selection method, found $count. No file was changed." }
$replacement = @'
    private List<AgentToolType> selectInitialTools(AgentRunRequest request) {
        String prompt = request.getPrompt().toLowerCase();
        List<AgentToolType> selected = new ArrayList<>();
        boolean wantsGeneration = contains(prompt,
                "\u751f\u6210\u4e00\u5f20", "\u751f\u6210\u56fe\u7247", "\u751f\u6210\u56fe\u50cf", "\u7ed8\u5236", "\u753b\u4e00\u5f20", "\u521b\u5efa\u6d77\u62a5", "\u6587\u751f\u56fe",
                "generate an image", "create an image", "draw an image");
        boolean wantsOcr = !request.getImageUrls().isEmpty() && contains(prompt,
                "\u8bc6\u522b", "\u63d0\u53d6\u6587\u5b57", "\u56fe\u4e2d\u6587\u5b57", "ocr", "extract text", "read text");
        boolean wantsEdit = contains(prompt,
                "\u7f16\u8f91", "\u80cc\u666f", "\u6e05\u6670", "\u4fee\u590d", "\u66ff\u6362", "\u5c40\u90e8", "edit", "background", "style");
        if (wantsGeneration) {
            selected.add(AgentToolType.IMAGE_GENERATE);
        } else if (wantsEdit) {
            selected.add(AgentToolType.IMAGE_EDIT);
        }
        if (wantsOcr) {
            selected.add(AgentToolType.OCR);
        }
        return selected;
    }

    private List<AgentToolResult> executeTools
'@
$updated = [regex]::Replace($content, $pattern, $replacement, 1)
[System.IO.File]::WriteAllText($path, $updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Applied generation-first and explicit-OCR-only routing for dynamic Plan-Solve."
