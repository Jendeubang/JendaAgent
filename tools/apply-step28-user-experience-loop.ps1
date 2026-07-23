$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$showcase = Join-Path $root "showcase"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Write-Source([string]$Base, [string]$RelativePath, [string]$Source) {
    $path = Join-Path $Base $RelativePath
    [System.IO.File]::WriteAllText($path, $Source.TrimStart([char]13, [char]10) + "`n", $utf8)
    Write-Host "Updated $path"
}

function Replace-Exactly([string]$Base, [string]$RelativePath, [string]$Old, [string]$New) {
    $path = Join-Path $Base $RelativePath
    $source = [System.IO.File]::ReadAllText($path)
    $count = ([regex]::Matches($source, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "Expected exactly one matching block in $path, found $count. No files were changed for this replacement." }
    [System.IO.File]::WriteAllText($path, $source.Replace($Old, $New), $utf8)
    Write-Host "Updated $path"
}

Write-Source $showcase "lib/agentAuth.ts" @'
export type AgentAuthSession = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  username: string;
};

const sessionKey = "jenda-agent-auth-session";
const userScopedKeys = ["jenda-agent-current-session-id", "jenda-image-studio-session"];

export function readAgentAuthSession(): AgentAuthSession | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const value = window.localStorage.getItem(sessionKey);
    return value ? JSON.parse(value) as AgentAuthSession : undefined;
  } catch {
    return undefined;
  }
}

export function saveAgentAuthSession(session: AgentAuthSession) {
  window.localStorage.setItem(sessionKey, JSON.stringify(session));
}

export function clearAgentAuthSession() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(sessionKey);
  userScopedKeys.forEach((key) => window.localStorage.removeItem(key));
}

export function agentHeaders(headers?: HeadersInit) {
  const result = new Headers(headers);
  const session = readAgentAuthSession();
  if (session?.accessToken) result.set("Authorization", `Bearer ${session.accessToken}`);
  return result;
}

function redirectForExpiredToken() {
  if (typeof window === "undefined" || window.location.pathname === "/login") return;
  clearAgentAuthSession();
  window.location.replace("/login?reason=expired");
}

/** Use only for JendaAgent API requests. COS object PUT requests must remain unsigned. */
export async function agentFetch(input: RequestInfo | URL, init: RequestInit = {}) {
  const response = await fetch(input, { ...init, headers: agentHeaders(init.headers) });
  if (response.status === 401) redirectForExpiredToken();
  return response;
}
'@

Write-Source $backend "src/main/java/com/jd/genie/controller/SignedCosAgentMediaController.java" @'
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
'@

Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/AgentAssetMetadataStore.java" '        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_asset_owner_session ON agent_asset (owner_user_id, session_id, created_at)");' @'
        try {
            jdbcTemplate.execute("CREATE INDEX idx_agent_asset_owner_session ON agent_asset (owner_user_id, session_id, created_at)");
        } catch (Exception ignored) {
            // MySQL does not support CREATE INDEX IF NOT EXISTS.
        }
'@

Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" 'import com.jd.genie.model.agent.DirectUploadTicketRequest;' 'import com.jd.genie.model.agent.DirectUploadTicketRequest;`r`nimport com.jd.genie.model.auth.AgentPrincipal;'
Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" '    private final CosSignedUrlService signedUrlService;' '    private final CosSignedUrlService signedUrlService;`r`n    private final AgentAssetMetadataStore assetStore;'
Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" 'public StsDirectUploadTicketService(Environment environment, ObjectMapper objectMapper, CosSignedUrlService signedUrlService) {' 'public StsDirectUploadTicketService(Environment environment, ObjectMapper objectMapper, CosSignedUrlService signedUrlService, AgentAssetMetadataStore assetStore) {'
Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" '        this.signedUrlService = signedUrlService;' '        this.signedUrlService = signedUrlService;`r`n        this.assetStore = assetStore;'
$oldComplete = @'
        return new AgentImageUploadResponse(uploadId, record.fileName, signedUrlService.createGetUrl(record.objectKey),
                record.mediaType, uploadedSize, true, "cos-sts");
'@
$newComplete = @'
        AgentImageUploadResponse response = new AgentImageUploadResponse(uploadId, record.fileName, signedUrlService.createGetUrl(record.objectKey),
                record.mediaType, uploadedSize, true, "cos-sts");
        assetStore.recordUpload(new AgentPrincipal(record.ownerUserId, "stored-owner"), record.sessionId, response, record.objectKey);
        return response;
'@
Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" $oldComplete $newComplete

Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/DynamicPlanSolveAgentRunService.java" '    private final HttpAgentToolClient toolClient;' '    private final HttpAgentToolClient toolClient;`r`n    private final AgentAssetMetadataStore assetStore;'
$oldImageBlock = @'
                    if (result.success() && result.imageUrl() != null && !result.imageUrl().isBlank()) {
                        publish(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE,
                                "ImageToolchain", Map.of(
                                "assetId", "asset-" + UUID.randomUUID(),
                                "round", round,
                                "title", result.tool().getDisplayName() + " output",
                                "imageUrl", result.imageUrl(),
                                "content", result.summary(),
                                "sourceTool", result.tool().name())));
                    }
'@
$newImageBlock = @'
                    if (result.success() && result.imageUrl() != null && !result.imageUrl().isBlank()) {
                        String assetId = "asset-" + UUID.randomUUID();
                        String assetTitle = result.tool().getDisplayName() + " output";
                        assetStore.recordGeneratedForSession(sessionId, runId, assetId, assetTitle, result.imageUrl());
                        publish(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE,
                                "ImageToolchain", Map.of(
                                "assetId", assetId,
                                "round", round,
                                "title", assetTitle,
                                "imageUrl", result.imageUrl(),
                                "content", result.summary(),
                                "sourceTool", result.tool().name())));
                    }
'@
Replace-Exactly $backend "src/main/java/com/jd/genie/service/agent/DynamicPlanSolveAgentRunService.java" $oldImageBlock $newImageBlock

Replace-Exactly $showcase "app/agent-studio/page.tsx" 'import { agentFetch } from "../../lib/agentAuth";' 'import { agentFetch } from "../../lib/agentAuth";`r`nimport { AgentAccountMenu } from "../../components/AgentAccountMenu";'
Replace-Exactly $showcase "app/agent-studio/page.tsx" '<Segmented value={mode}' '<AgentAccountMenu /><Segmented value={mode}'
Replace-Exactly $showcase "app/image-studio/page.tsx" 'import { agentFetch } from "../../lib/agentAuth";' 'import { agentFetch } from "../../lib/agentAuth";`r`nimport { AgentAccountMenu } from "../../components/AgentAccountMenu";'
Replace-Exactly $showcase "app/image-studio/page.tsx" '        <Button icon={<ReloadOutlined />} onClick={() => void loadWorkspace()} disabled={!sessionId || running}>{copy.refreshAssets}</Button>' '        <Button icon={<ReloadOutlined />} onClick={() => void loadWorkspace()} disabled={!sessionId || running}>{copy.refreshAssets}</Button>`r`n        <AgentAccountMenu />'

Write-Host "Step 28 user experience loop changes completed. Compile backend and restart both services."
