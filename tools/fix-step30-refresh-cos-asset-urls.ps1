$ErrorActionPreference = "Stop"

# Re-sign private COS URLs whenever an asset page is read. This keeps old asset
# records usable after the original short-lived URL has expired.
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$servicePath = Join-Path $backend "src/main/java/com/jd/genie/service/agent/AgentAssetService.java"
$cosPath = Join-Path $backend "src/main/java/com/jd/genie/service/agent/CosAgentImageStorage.java"
$utf8 = New-Object System.Text.UTF8Encoding($false)

foreach ($path in @($servicePath, $cosPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Required source file is missing: $path" }
}

$cosSource = [System.IO.File]::ReadAllText($cosPath)
$serviceSource = [System.IO.File]::ReadAllText($servicePath)

$deleteUrlPattern = '(?ms)^    public void deleteObjectFromUrl\(String imageUrl\) \{.*?^    \}\r?\n\r?\n(?=    private void putObject)'
$deleteUrlMatches = [regex]::Matches($cosSource, $deleteUrlPattern)
if ($cosSource.Contains("public String resolveManagedObjectKey(String imageUrl)")) {
    $updatedCosSource = $cosSource
} elseif ($deleteUrlMatches.Count -eq 1) {
    $lineEnding = if ($cosSource.Contains("`r`n")) { "`r`n" } else { "`n" }
    $replacement = @(
        '    public void deleteObjectFromUrl(String imageUrl) {',
        '        deleteObject(resolveManagedObjectKey(imageUrl));',
        '    }',
        '',
        '    public String resolveManagedObjectKey(String imageUrl) {',
        '        try {',
        '            URI uri = URI.create(imageUrl);',
        '            String objectKey = java.net.URLDecoder.decode(uri.getRawPath().replaceFirst("^/", ""), StandardCharsets.UTF_8);',
        '            if (objectKey.isBlank() || !objectKey.startsWith(prefix)) {',
        '                throw new IllegalArgumentException("COS URL is outside the configured prefix");',
        '            }',
        '            return objectKey;',
        '        } catch (IllegalArgumentException error) {',
        '            throw error;',
        '        } catch (Exception error) {',
        '            throw new IllegalStateException("Unable to resolve COS object key from asset URL", error);',
        '        }',
        '    }',
        ''
    ) -join $lineEnding
    $updatedCosSource = [regex]::Replace($cosSource, $deleteUrlPattern, [System.Text.RegularExpressions.MatchEvaluator]{ param($match) $replacement }, 1)
} else {
    throw "Expected one deleteObjectFromUrl method in $cosPath, found $($deleteUrlMatches.Count). No files were changed."
}

if ($serviceSource.Contains("private AgentAssetMetadata refreshImageUrl")) {
    $updatedServiceSource = $serviceSource
} elseif ($serviceSource.Contains("public AgentAssetPage page(")) {
    $updatedServiceSource = @'
package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/** Coordinates asset access, fresh private COS URLs, and destructive COS cleanup. */
@Service
@RequiredArgsConstructor
public class AgentAssetService {
    private final AgentAssetMetadataStore metadataStore;
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;

    public AgentAssetPage page(String ownerUserId, String sessionId, int page, int size) {
        AgentAssetPage storedPage = metadataStore.pageOwned(ownerUserId, sessionId, page, size);
        List<AgentAssetMetadata> refreshed = storedPage.items().stream().map(this::refreshImageUrl).toList();
        return new AgentAssetPage(refreshed, storedPage.total(), storedPage.page(), storedPage.size());
    }

    public AgentAssetMetadata delete(String ownerUserId, String assetId) {
        AgentAssetMetadata asset = metadataStore.findOwned(ownerUserId, assetId).orElseThrow(AgentSessionAccessDeniedException::new);
        CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
        if (cosStorage != null) {
            if (asset.objectKey() != null && !asset.objectKey().isBlank()) {
                cosStorage.deleteObject(asset.objectKey());
            } else {
                cosStorage.deleteObjectFromUrl(asset.imageUrl());
            }
        }
        metadataStore.deleteOwned(ownerUserId, assetId);
        return asset;
    }

    private AgentAssetMetadata refreshImageUrl(AgentAssetMetadata asset) {
        CosSignedUrlService signedUrlService = signedUrlServiceProvider.getIfAvailable();
        CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
        if (signedUrlService == null) return asset;

        String objectKey = asset.objectKey();
        if ((objectKey == null || objectKey.isBlank()) && cosStorage != null) {
            try {
                objectKey = cosStorage.resolveManagedObjectKey(asset.imageUrl());
            } catch (RuntimeException ignored) {
                return asset;
            }
        }
        if (objectKey == null || objectKey.isBlank()) return asset;
        return new AgentAssetMetadata(asset.assetId(), asset.ownerUserId(), asset.sessionId(), asset.runId(), asset.fileName(), asset.mediaType(), asset.size(), objectKey, signedUrlService.createGetUrl(objectKey), asset.source(), asset.createdAt());
    }
}
'@
} else {
    throw "AgentAssetService source has an unexpected structure. No files were changed."
}

[System.IO.File]::WriteAllText($cosPath, $updatedCosSource, $utf8)
[System.IO.File]::WriteAllText($servicePath, $updatedServiceSource, $utf8)
Write-Host "Updated $cosPath"
Write-Host "Updated $servicePath"
Write-Host "Asset URL refresh fix completed. Rebuild and restart the backend."
