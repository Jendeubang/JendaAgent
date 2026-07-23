package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentRequestUserContext;
import com.jd.genie.service.agent.AgentRunService;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/agent")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentRunController {
    private final AgentRunService agentRunService;

    @PostMapping(value = "/sessions/{sessionId}/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String sessionId, @Valid @RequestBody AgentRunRequest request, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return AgentRequestUserContext.runAs(principal, () -> agentRunService.startRun(sessionId, request));
    }
}
