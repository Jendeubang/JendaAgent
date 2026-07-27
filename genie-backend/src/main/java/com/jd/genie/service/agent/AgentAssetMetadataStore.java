package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.StoredAgentImage;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.persistence.agent.service.AgentOperationalPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** User-scoped COS asset metadata facade backed by MyBatis-Flex. */
@Repository
@RequiredArgsConstructor
public class AgentAssetMetadataStore {
    private final AgentOperationalPersistenceService persistence;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;

    public void recordUpload(AgentPrincipal owner, String sessionId, AgentImageUploadResponse response, String objectKey) {
        persistence.upsertAsset(new AgentAssetMetadata(response.assetId(), owner.userId(), sessionId, null,
                response.fileName(), response.mediaType(), response.size(), objectKey, response.imageUrl(), "upload", Instant.now()));
    }

    public void recordGenerated(AgentPrincipal owner, String sessionId, String runId,
                                StoredAgentImage storedImage, String imageUrl, String source) {
        persistence.upsertAsset(new AgentAssetMetadata(storedImage.assetId(), owner.userId(), sessionId, runId,
                storedImage.originalFileName(), storedImage.mediaType(), storedImage.size(), storedImage.storedFileName(),
                imageUrl, source, Instant.now()));
    }

    public void recordGeneratedForSession(String sessionId, String runId, String assetId, String title, String imageUrl) {
        String ownerUserId = persistence.ownerOfSession(sessionId);
        if (ownerUserId == null) return;
        CosSignedUrlService signer = signedUrlServiceProvider.getIfAvailable();
        String objectKey = signer == null ? null : signer.objectKeyIfOwned(imageUrl);
        String currentUrl = objectKey == null || signer == null ? imageUrl : signer.createGetUrl(objectKey);
        persistence.upsertAsset(new AgentAssetMetadata(assetId, ownerUserId, sessionId, runId, title,
                "image/*", 0L, objectKey, currentUrl, "generated", Instant.now()));
    }

    public AgentAssetPage pageOwned(String ownerUserId, String sessionId, int page, int size) {
        return persistence.pageAssets(ownerUserId, sessionId, page, size);
    }

    public Optional<AgentAssetMetadata> findOwned(String ownerUserId, String assetId) {
        return persistence.findOwnedAsset(ownerUserId, assetId);
    }

    public boolean deleteOwned(String ownerUserId, String assetId) {
        return persistence.deleteOwnedAsset(ownerUserId, assetId);
    }
}