$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\ModelToolAgentRunService.java"
$utf8 = [System.Text.UTF8Encoding]::new($false)
$source = [System.IO.File]::ReadAllText($path)

function Replace-Once([string]$Old, [string]$New, [string]$Label) {
    $count = ([regex]::Matches($script:source, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "Expected exactly one $Label block, found $count. No files were changed." }
    $script:source = $script:source.Replace($Old, $New)
}

$newline = [Environment]::NewLine
Replace-Once '    private final HttpAgentToolClient toolClient;' ('    private final HttpAgentToolClient toolClient;' + $newline + '    private final AgentAssetMetadataStore assetStore;') 'asset store field'
$ifLine = '                if (result.success() && result.imageUrl() != null && !result.imageUrl().isBlank()) {'
$ifReplacement = $ifLine + $newline + '                    String assetId = "asset-" + UUID.randomUUID();' + $newline + '                    assetStore.recordGeneratedForSession(sessionId, runId, assetId, result.tool().getDisplayName() + " output", result.imageUrl());'
Replace-Once $ifLine $ifReplacement 'generated image event'
Replace-Once '                            "assetId", "asset-" + UUID.randomUUID(),' '                            "assetId", assetId,' 'generated asset id'

[System.IO.File]::WriteAllText($path, $source, $utf8)
Write-Host "Added v1 generated-image metadata persistence. Rebuild the backend."
