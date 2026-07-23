package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.StoredAgentImage;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentAssetMetadataStore;
import com.jd.genie.service.agent.AgentHistoryStore;
import com.jd.genie.service.agent.AgentImageStorage;
import com.jd.genie.service.agent.CosAgentImageStorage;
import com.jd.genie.service.agent.CosSignedUrlService;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** COS fallback upload with explicit user and session metadata persistence. */
@RestController
@RequestMapping("/api/v1/agent/media")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class SignedCosAgentMediaController extends CosAgentMediaController {
    private final CosAgentImageStorage cosStorage;
    private final CosSignedUrlService signedUrlService;
    private final AgentHistoryStore historyStore;
    private final AgentAssetMetadataStore assetStore;

    public SignedCosAgentMediaController(AgentImageStorage imageStorage, CosAgentImageStorage cosStorage, CosSignedUrlService signedUrlService, AgentHistoryStore historyStore, AgentAssetMetadataStore assetStore) {
        super(imageStorage, cosStorage);
        this.cosStorage = cosStorage;
        this.signedUrlService = signedUrlService;
        this.historyStore = historyStore;
        this.assetStore = assetStore;
    }

    @Override
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AgentImageUploadResponse uploadImage(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
        AgentPrincipal principal = (AgentPrincipal) request.getAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        String sessionId = request.getParameter("sessionId");
        if (principal == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Authenticated sessionId is required for image upload");
        }
        historyStore.claimSession(principal.userId(), sessionId);
        StoredAgentImage storedImage = cosStorage.store(file);
        AgentImageUploadResponse response = new AgentImageUploadResponse(storedImage.assetId(), storedImage.originalFileName(), signedUrlService.createGetUrl(storedImage.storedFileName()), storedImage.mediaType(), storedImage.size(), true, "cos");
        assetStore.recordUpload(principal, sessionId, response, storedImage.storedFileName());
        return response;
    }
}
