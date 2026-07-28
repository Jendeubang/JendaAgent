package com.jd.genie.controller;

import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentRequestUserContext;
import com.jd.genie.service.agent.plansolve.AgentToolCapability;
import com.jd.genie.service.agent.plansolve.AgentToolCapabilityRegistry;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated capability discovery for agent clients and diagnostics. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent/tool-capabilities")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentToolCapabilityController {
    private final AgentToolCapabilityRegistry registry;

    @GetMapping
    public List<AgentToolCapability> capabilities(@RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return AgentRequestUserContext.runAs(principal, registry::snapshot);
    }
}