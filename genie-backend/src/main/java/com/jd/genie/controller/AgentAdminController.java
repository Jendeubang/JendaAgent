package com.jd.genie.controller;

import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.model.security.AgentPlanUpdateRequest;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import com.jd.genie.service.security.AgentBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AgentAdminController {
    private final AgentBillingService billingService;
    private final AgentAuthProperties properties;

    @PostMapping("/users/{userId}/plan")
    public void updatePlan(@PathVariable String userId, @Valid @RequestBody AgentPlanUpdateRequest request,
                           @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        if (!properties.getAdminUsernames().contains(principal.username())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
        billingService.setPlan(userId, request.planCode());
    }
}