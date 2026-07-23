$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$requestPath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\model\agent\AgentToolGatewayRequest.java"
$ocrPath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\controller\QwenOcrToolGatewayController.java"

function Replace-Once([string]$Path, [string]$Old, [string]$New) {
    $content = Get-Content -Raw -LiteralPath $Path
    $count = ([regex]::Matches($content, [regex]::Escape($Old))).Count
    if ($count -ne 1) {
        throw "Expected exactly one matching block in $Path, found $count. No files were changed."
    }
    [System.IO.File]::WriteAllText($Path, $content.Replace($Old, $New), [System.Text.UTF8Encoding]::new($false))
    Write-Host "Updated $Path"
}

$requestContent = Get-Content -Raw -LiteralPath $requestPath
if ($requestContent.Contains("import jakarta.validation.constraints.NotEmpty;")) {
    Replace-Once $requestPath "import jakarta.validation.constraints.NotEmpty;`r`n" ""
    Replace-Once $requestPath "@NotEmpty @Size(max = 10) List<String> image_urls," "@Size(max = 10) List<String> image_urls,"
} elseif (-not $requestContent.Contains("@Size(max = 10) List<String> image_urls,")) {
    throw "Unexpected request DTO state in $requestPath. No files were changed."
} else {
    Write-Host "Request DTO already permits image generation without reference images."
}

$ocrContent = Get-Content -Raw -LiteralPath $ocrPath
$marker = "OCR requires at least one image_url"
if (-not $ocrContent.Contains($marker)) {
    $old = @'
        if (!"ocr".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be ocr");
        }
        return Map.of("text", client.recognize(request), "provider", "qwen-ocr");
'@.TrimStart("`n")
    $new = @'
        if (!"ocr".equalsIgnoreCase(request.task_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "task_type must be ocr");
        }
        if (request.image_urls() == null || request.image_urls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR requires at least one image_url");
        }
        return Map.of("text", client.recognize(request), "provider", "qwen-ocr");
'@.TrimStart("`n")
    Replace-Once $ocrPath $old $new
} else {
    Write-Host "OCR image validation already exists."
}

Write-Host "Step 21 image-generation validation fix completed. Rebuild and restart the backend."
