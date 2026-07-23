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