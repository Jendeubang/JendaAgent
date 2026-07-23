package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentSessionOverview;
import com.jd.genie.model.agent.AgentWorkspaceSnapshot;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.PersistentAgentWorkspaceCatalog;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@ConditionalOnExpression("'${agent.history.persistence:file}' == 'file' or '${agent.history.persistence:file}' == 'mysql'")
@RequestMapping("/api/v1/agent/sessions")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentWorkspaceController {
    private final PersistentAgentWorkspaceCatalog catalog;

    @GetMapping
    public List<AgentSessionOverview> sessions(@RequestParam(defaultValue = "40") int limit, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return catalog.list(principal.userId(), limit);
    }

    @GetMapping("/{sessionId}/workspace")
    public AgentWorkspaceSnapshot workspace(@PathVariable String sessionId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return catalog.workspace(principal.userId(), sessionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }
}
