package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentHistoryStore;
import com.jd.genie.service.agent.AgentRequestUserContext;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentHistoryController {
    private final AgentHistoryStore historyStore;

    @GetMapping("/sessions/{sessionId}/events")
    public List<AgentEvent> replay(@PathVariable String sessionId, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return AgentRequestUserContext.runAs(principal, () -> historyStore.replay(sessionId));
    }
}
