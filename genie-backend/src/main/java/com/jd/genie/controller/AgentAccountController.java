package com.jd.genie.controller;

import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.model.security.AgentUsageSummary;
import com.jd.genie.service.auth.AgentAuthenticationFilter;
import com.jd.genie.service.security.AgentBillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account")
public class AgentAccountController {
    private final AgentBillingService billingService;

    @GetMapping("/usage")
    public AgentUsageSummary usage(@RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return billingService.summary(principal);
    }
}