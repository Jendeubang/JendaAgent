package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentAssetMetadata;
import com.jd.genie.model.agent.AgentAssetPage;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentAssetMetadataStore;
import com.jd.genie.service.agent.AgentAssetService;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Paginated asset library and destructive COS cleanup API for the current JWT user. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/assets")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentAssetController {
    private final AgentAssetMetadataStore assetStore;
    private final AgentAssetService assetService;

    @GetMapping
    public AgentAssetPage assets(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "12") int size, @RequestParam(required = false) String sessionId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return assetService.page(principal.userId(), sessionId, page, size);
    }

    @GetMapping("/{assetId}")
    public AgentAssetMetadata asset(@PathVariable String assetId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return assetStore.findOwned(principal.userId(), assetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    @DeleteMapping("/{assetId}")
    public AgentAssetMetadata delete(@PathVariable String assetId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return assetService.delete(principal.userId(), assetId);
    }
}
