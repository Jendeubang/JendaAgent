package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentImageUploadResponse;
import com.jd.genie.model.agent.DirectUploadCompleteRequest;
import com.jd.genie.model.agent.DirectUploadTicket;
import com.jd.genie.model.agent.DirectUploadTicketRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentHistoryStore;
import com.jd.genie.service.agent.AgentRequestUserContext;
import com.jd.genie.service.agent.StsDirectUploadTicketService;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.storage.provider", havingValue = "cos")
@RequestMapping("/api/v1/agent/media/direct")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class AgentDirectUploadController {
    private final StsDirectUploadTicketService ticketService;
    private final AgentHistoryStore historyStore;

    @PostMapping("/tickets")
    public DirectUploadTicket issue(@Valid @RequestBody DirectUploadTicketRequest request, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        historyStore.claimSession(principal.userId(), request.getSessionId());
        return AgentRequestUserContext.runAs(principal, () -> ticketService.issue(request));
    }

    @PostMapping("/complete")
    public AgentImageUploadResponse complete(@Valid @RequestBody DirectUploadCompleteRequest request, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return AgentRequestUserContext.runAs(principal, () -> ticketService.complete(request.getUploadId()));
    }
}
