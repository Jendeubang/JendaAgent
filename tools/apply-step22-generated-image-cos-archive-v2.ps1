$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\controller\QwenImageGenerateToolGatewayController.java"
$content = Get-Content -Raw -LiteralPath $path

if ($content.Contains("GeneratedImageCosArchiver")) {
    Write-Host "Step 22 controller changes are already present."
    exit 0
}

$oldImport = "import com.jd.genie.service.agent.QwenImageGenerateGatewayClient;"
$newImport = "import com.jd.genie.service.agent.GeneratedImageCosArchiver;" + [Environment]::NewLine + $oldImport
$oldField = "    private final QwenImageGenerateGatewayClient client;"
$newField = $oldField + [Environment]::NewLine + "    private final GeneratedImageCosArchiver imageCosArchiver;"
$pattern = '(?s)        QwenImageGenerateGatewayClient\.GeneratedImage result = client\.generate\(request\);\s*        return Map\.of\(\s*                "image_url", result\.imageUrl\(\),\s*                "text", result\.text\(\),\s*                "provider", "qwen-image"\);'
$matches = [regex]::Matches($content, $pattern)

if ($content.IndexOf($oldImport) -lt 0 -or $content.IndexOf($oldField) -lt 0 -or $matches.Count -ne 1) {
    throw "Expected one controller import, field, and generation block. No files were changed."
}

$replacement = @'
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

$updated = $content.Replace($oldImport, $newImport).Replace($oldField, $newField)
$updated = [regex]::Replace($updated, $pattern, $replacement, 1)
[System.IO.File]::WriteAllText($path, $updated, [System.Text.UTF8Encoding]::new($false))
Write-Host "Updated $path"
Write-Host "Step 22 generated-image COS archive changes completed. Rebuild and restart the backend."
