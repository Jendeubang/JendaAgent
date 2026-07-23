$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\controller\QwenImageGenerateToolGatewayController.java"
$content = Get-Content -Raw -LiteralPath $path

if ($content.Contains("GeneratedImageCosArchiver")) {
    Write-Host "Step 22 controller changes are already present."
    exit 0
}

$oldImport = "import com.jd.genie.service.agent.QwenImageGenerateGatewayClient;"
$newImport = @"
import com.jd.genie.service.agent.GeneratedImageCosArchiver;
import com.jd.genie.service.agent.QwenImageGenerateGatewayClient;
"@.Trim()
$oldField = "    private final QwenImageGenerateGatewayClient client;"
$newField = @"
    private final QwenImageGenerateGatewayClient client;
    private final GeneratedImageCosArchiver imageCosArchiver;
"@.TrimEnd()
$oldBlock = @'
        QwenImageGenerateGatewayClient.GeneratedImage result = client.generate(request);
        return Map.of(
                "image_url", result.imageUrl(),
                "text", result.text(),
                "provider", "qwen-image");
'@.TrimStart("`n")
$newBlock = @'
        QwenImageGenerateGatewayClient.GeneratedImage result = client.generate(request);
        GeneratedImageCosArchiver.ArchiveResult archived = imageCosArchiver.archive(result.imageUrl());
        String text = archived.archived()
                ? result.text() + "; archived to COS"
                : result.text() + "; COS archive fallback: " + archived.detail();
        return Map.of(
                "image_url", archived.imageUrl(),
                "text", text,
                "provider", archived.archived() ? "qwen-image-cos" : "qwen-image");
'@.TrimStart("`n")

function Count([string]$Value, [string]$Needle) {
    return ([regex]::Matches($Value, [regex]::Escape($Needle))).Count
}

if ((Count $content $oldImport) -ne 1 -or (Count $content $oldField) -ne 1 -or (Count $content $oldBlock) -ne 1) {
    throw "Expected one controller import, field, and generation block. No files were changed."
}

$updated = $content.Replace($oldImport, $newImport).Replace($oldField, $newField).Replace($oldBlock, $newBlock)
[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"
Write-Host "Step 22 generated-image COS archive changes completed. Rebuild and restart the backend."
