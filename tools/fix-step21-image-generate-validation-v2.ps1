$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$requestPath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\model\agent\AgentToolGatewayRequest.java"
$ocrPath = Join-Path $root "genie-backend\src\main\java\com\jd\genie\controller\QwenOcrToolGatewayController.java"
$utf8 = [System.Text.UTF8Encoding]::new($false)

$requestOriginal = Get-Content -Raw -LiteralPath $requestPath
$ocrOriginal = Get-Content -Raw -LiteralPath $ocrPath

$requestUpdated = $requestOriginal
if ($requestUpdated.Contains("@NotEmpty @Size(max = 10) List<String> image_urls,")) {
    $requestUpdated = [regex]::Replace($requestUpdated, '(?m)^import jakarta\.validation\.constraints\.NotEmpty;\r?\n', '')
    $requestUpdated = $requestUpdated.Replace("@NotEmpty @Size(max = 10) List<String> image_urls,", "@Size(max = 10) List<String> image_urls,")
}
if (-not $requestUpdated.Contains("@Size(max = 10) List<String> image_urls,")) {
    throw "Request DTO does not contain the expected image_urls declaration. No files were changed."
}
if ($requestUpdated.Contains("import jakarta.validation.constraints.NotEmpty;")) {
    throw "Request DTO still imports NotEmpty after replacement. No files were changed."
}

$ocrUpdated = $ocrOriginal
if (-not $ocrUpdated.Contains("OCR requires at least one image_url")) {
    $pattern = '(?s)(if \(!"ocr"\.equalsIgnoreCase\(request\.task_type\(\)\)\) \{\s*throw new ResponseStatusException\(HttpStatus\.BAD_REQUEST, "task_type must be ocr"\);\s*\})\s*(return Map\.of\("text", client\.recognize\(request\), "provider", "qwen-ocr"\);)'
    $matches = [regex]::Matches($ocrUpdated, $pattern)
    if ($matches.Count -ne 1) {
        throw "Expected one OCR validation insertion point, found $($matches.Count). No files were changed."
    }
    $ocrUpdated = [regex]::Replace($ocrUpdated, $pattern, {
        param($match)
        $match.Groups[1].Value + [Environment]::NewLine +
        '        if (request.image_urls() == null || request.image_urls().isEmpty()) {' + [Environment]::NewLine +
        '            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OCR requires at least one image_url");' + [Environment]::NewLine +
        '        }' + [Environment]::NewLine +
        '        ' + $match.Groups[2].Value
    }, 1)
}
if (-not $ocrUpdated.Contains("OCR requires at least one image_url")) {
    throw "OCR validation was not prepared. No files were changed."
}

[System.IO.File]::WriteAllText($requestPath, $requestUpdated, $utf8)
[System.IO.File]::WriteAllText($ocrPath, $ocrUpdated, $utf8)
Write-Host "Updated $requestPath"
Write-Host "Updated $ocrPath"
Write-Host "Step 21 image-generation validation fix completed. Rebuild and restart the backend."
