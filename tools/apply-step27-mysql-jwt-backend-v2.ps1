$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$templatePath = Join-Path $PSScriptRoot "apply-step27-mysql-jwt-backend.ps1"
$template = [System.IO.File]::ReadAllText($templatePath)

# v1 contains line-based controller replacements whose newline literals are unsafe in PowerShell.
$template = [regex]::Replace($template, '(?m)^Replace-Exactly "src/main/java/com/jd/genie/controller/AgentDirectUploadController\.java".*\r?\n', '')
$marker = 'Replace-Exactly "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" ''tickets.put(uploadId, new TicketRecord(request.getFileName(), request.getMediaType(), request.getSize(), objectKey, expiresAt));'''
if (-not $template.Contains($marker)) { throw "Step 27 template marker was not found. No source files were changed." }

$controller = @'
Write-Source "src/main/java/com/jd/genie/controller/AgentDirectUploadController.java" @'
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
'@
'@

$template = $template.Replace($marker, $controller + "`r`n" + $marker)
$tempPath = Join-Path $PSScriptRoot "apply-step27-mysql-jwt-backend-v2.generated.ps1"
[System.IO.File]::WriteAllText($tempPath, $template, [System.Text.UTF8Encoding]::new($false))
try {
    & $tempPath
} finally {
    Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
}
