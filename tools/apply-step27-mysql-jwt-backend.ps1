$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root "genie-backend"
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Write-Source([string]$RelativePath, [string]$Source) {
    $path = Join-Path $backend $RelativePath
    [System.IO.File]::WriteAllText($path, $Source.TrimStart([char]13, [char]10) + "`n", $utf8)
    Write-Host "Updated $path"
}

function Replace-Exactly([string]$RelativePath, [string]$Old, [string]$New) {
    $path = Join-Path $backend $RelativePath
    $source = [System.IO.File]::ReadAllText($path)
    $count = ([regex]::Matches($source, [regex]::Escape($Old))).Count
    if ($count -ne 1) { throw "Expected exactly one matching block in $path, found $count. No files were changed." }
    [System.IO.File]::WriteAllText($path, $source.Replace($Old, $New), $utf8)
    Write-Host "Updated $path"
}

Write-Source "src/main/java/com/jd/genie/service/agent/AgentHistoryStore.java" @'
package com.jd.genie.service.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Stores typed agent events and enforces one owner for every session. */
@Repository
@RequiredArgsConstructor
public class AgentHistoryStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void initializeSchema() {
        execute("CREATE TABLE IF NOT EXISTS agent_session (session_id VARCHAR(64) PRIMARY KEY, owner_user_id VARCHAR(64) NOT NULL, latest_run_id VARCHAR(64) NOT NULL, mode VARCHAR(32) NOT NULL, latest_prompt TEXT NOT NULL, run_status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS agent_session_claim (session_id VARCHAR(64) PRIMARY KEY, owner_user_id VARCHAR(64) NOT NULL, claimed_at TIMESTAMP NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS agent_run (run_id VARCHAR(64) PRIMARY KEY, session_id VARCHAR(64) NOT NULL, owner_user_id VARCHAR(64) NOT NULL, mode VARCHAR(32) NOT NULL, prompt TEXT NOT NULL, image_urls_json TEXT, status VARCHAR(32) NOT NULL, created_at TIMESTAMP NOT NULL, completed_at TIMESTAMP)");
        execute("CREATE TABLE IF NOT EXISTS agent_message (event_id VARCHAR(64) PRIMARY KEY, session_id VARCHAR(64) NOT NULL, run_id VARCHAR(64) NOT NULL, sequence_no BIGINT NOT NULL, message_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL, agent_name VARCHAR(64) NOT NULL, occurred_at TIMESTAMP NOT NULL, payload_json TEXT NOT NULL, CONSTRAINT uk_agent_message_sequence UNIQUE (run_id, sequence_no))");
        execute("CREATE TABLE IF NOT EXISTS agent_plan_message (event_id VARCHAR(64) PRIMARY KEY, title VARCHAR(255), content TEXT, steps_json TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_prompt_optimization_message (event_id VARCHAR(64) PRIMARY KEY, original_prompt TEXT, optimized_prompt TEXT, retrieved_rules_json TEXT, provider VARCHAR(64), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_task_message (event_id VARCHAR(64) PRIMARY KEY, task_id VARCHAR(64), title VARCHAR(255), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_call_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, request_json TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_tool_result_message (event_id VARCHAR(64) PRIMARY KEY, tool_name VARCHAR(255), title VARCHAR(255), content TEXT, result_json TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_image_message (event_id VARCHAR(64) PRIMARY KEY, asset_id VARCHAR(64), image_url TEXT, title VARCHAR(255), content TEXT)");
        execute("CREATE TABLE IF NOT EXISTS agent_summary_message (event_id VARCHAR(64) PRIMARY KEY, title VARCHAR(255), content TEXT)");
        tryExecute("ALTER TABLE agent_session ADD COLUMN IF NOT EXISTS owner_user_id VARCHAR(64)");
        tryExecute("ALTER TABLE agent_run ADD COLUMN IF NOT EXISTS owner_user_id VARCHAR(64)");
        tryExecute("UPDATE agent_session SET owner_user_id = 'legacy-import' WHERE owner_user_id IS NULL");
        tryExecute("UPDATE agent_run SET owner_user_id = 'legacy-import' WHERE owner_user_id IS NULL");
    }

    public void claimSession(String ownerUserId, String sessionId) {
        String currentOwner = ownerOfClaim(sessionId);
        if (currentOwner == null) {
            jdbcTemplate.update("INSERT INTO agent_session_claim (session_id, owner_user_id, claimed_at) VALUES (?, ?, ?)",
                    sessionId, ownerUserId, Timestamp.from(Instant.now()));
        } else if (!currentOwner.equals(ownerUserId)) {
            throw new AgentSessionAccessDeniedException();
        }
        String persistedOwner = ownerOfSession(sessionId);
        if (persistedOwner != null && !persistedOwner.equals(ownerUserId)) {
            throw new AgentSessionAccessDeniedException();
        }
    }

    public boolean canAccess(String ownerUserId, String sessionId) {
        String persistedOwner = ownerOfSession(sessionId);
        if (persistedOwner != null) return persistedOwner.equals(ownerUserId);
        String claimedOwner = ownerOfClaim(sessionId);
        return claimedOwner != null && claimedOwner.equals(ownerUserId);
    }

    public void startRun(String sessionId, String runId, AgentRunRequest request) {
        String ownerUserId = AgentRequestUserContext.current().userId();
        claimSession(ownerUserId, sessionId);
        Instant now = Instant.now();
        String existingOwner = ownerOfSession(sessionId);
        if (existingOwner == null) {
            jdbcTemplate.update("INSERT INTO agent_session (session_id, owner_user_id, latest_run_id, mode, latest_prompt, run_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    sessionId, ownerUserId, runId, request.getMode().getValue(), request.getPrompt(), AgentEventStatus.RUNNING.getValue(), Timestamp.from(now), Timestamp.from(now));
        } else {
            int updated = jdbcTemplate.update("UPDATE agent_session SET latest_run_id = ?, mode = ?, latest_prompt = ?, run_status = ?, updated_at = ? WHERE session_id = ? AND owner_user_id = ?",
                    runId, request.getMode().getValue(), request.getPrompt(), AgentEventStatus.RUNNING.getValue(), Timestamp.from(now), sessionId, ownerUserId);
            if (updated != 1) throw new AgentSessionAccessDeniedException();
        }
        jdbcTemplate.update("INSERT INTO agent_run (run_id, session_id, owner_user_id, mode, prompt, image_urls_json, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                runId, sessionId, ownerUserId, request.getMode().getValue(), request.getPrompt(), toJson(request.getImageUrls()), AgentEventStatus.RUNNING.getValue(), Timestamp.from(now));
    }

    public void append(AgentEvent event) {
        jdbcTemplate.update("INSERT INTO agent_message (event_id, session_id, run_id, sequence_no, message_type, status, agent_name, occurred_at, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                event.eventId(), event.sessionId(), event.runId(), event.sequence(), event.messageType().getValue(), event.status().getValue(), event.agent(), Timestamp.from(event.occurredAt()), toJson(event.payload()));
        routeTypedMessage(event);
    }

    public void completeRun(String sessionId, String runId, AgentEventStatus status) {
        Instant now = Instant.now();
        jdbcTemplate.update("UPDATE agent_run SET status = ?, completed_at = ? WHERE run_id = ?", status.getValue(), Timestamp.from(now), runId);
        jdbcTemplate.update("UPDATE agent_session SET run_status = ?, updated_at = ? WHERE session_id = ?", status.getValue(), Timestamp.from(now), sessionId);
    }

    public List<AgentEvent> replay(String sessionId) {
        String ownerUserId = AgentRequestUserContext.current().userId();
        if (!canAccess(ownerUserId, sessionId)) throw new AgentSessionAccessDeniedException();
        return jdbcTemplate.query("SELECT event_id, session_id, run_id, sequence_no, message_type, status, agent_name, occurred_at, payload_json FROM agent_message WHERE session_id = ? ORDER BY occurred_at, sequence_no",
                (resultSet, rowNum) -> new AgentEvent("v1", resultSet.getString("event_id"), resultSet.getString("session_id"), resultSet.getString("run_id"), resultSet.getLong("sequence_no"), parseType(resultSet.getString("message_type")), parseStatus(resultSet.getString("status")), resultSet.getString("agent_name"), resultSet.getTimestamp("occurred_at").toInstant(), fromJson(resultSet.getString("payload_json"))), sessionId);
    }

    private void routeTypedMessage(AgentEvent event) {
        Map<String, Object> payload = event.payload();
        switch (event.messageType()) {
            case PLAN -> jdbcTemplate.update("INSERT INTO agent_plan_message (event_id, title, content, steps_json) VALUES (?, ?, ?, ?)", event.eventId(), text(payload, "title"), text(payload, "content"), toJson(payload.get("steps")));
            case PROMPT_OPTIMIZATION -> jdbcTemplate.update("INSERT INTO agent_prompt_optimization_message (event_id, original_prompt, optimized_prompt, retrieved_rules_json, provider, content) VALUES (?, ?, ?, ?, ?, ?)", event.eventId(), text(payload, "originalPrompt"), text(payload, "optimizedPrompt"), toJson(payload.get("retrievedRules")), text(payload, "provider"), text(payload, "content"));
            case TASK -> jdbcTemplate.update("INSERT INTO agent_task_message (event_id, task_id, title, content) VALUES (?, ?, ?, ?)", event.eventId(), text(payload, "taskId"), text(payload, "title"), text(payload, "content"));
            case TOOL_CALL -> jdbcTemplate.update("INSERT INTO agent_tool_call_message (event_id, tool_name, title, content, request_json) VALUES (?, ?, ?, ?, ?)", event.eventId(), text(payload, "tool"), text(payload, "title"), text(payload, "content"), toJson(payload));
            case TOOL_RESULT -> jdbcTemplate.update("INSERT INTO agent_tool_result_message (event_id, tool_name, title, content, result_json) VALUES (?, ?, ?, ?, ?)", event.eventId(), text(payload, "tool"), text(payload, "title"), text(payload, "content"), toJson(payload));
            case IMAGE -> jdbcTemplate.update("INSERT INTO agent_image_message (event_id, asset_id, image_url, title, content) VALUES (?, ?, ?, ?, ?)", event.eventId(), text(payload, "assetId"), text(payload, "imageUrl"), text(payload, "title"), text(payload, "content"));
            case SUMMARY -> jdbcTemplate.update("INSERT INTO agent_summary_message (event_id, title, content) VALUES (?, ?, ?)", event.eventId(), text(payload, "title"), text(payload, "content"));
            default -> { }
        }
    }

    private String ownerOfClaim(String sessionId) {
        List<String> owners = jdbcTemplate.query("SELECT owner_user_id FROM agent_session_claim WHERE session_id = ?", (resultSet, rowNum) -> resultSet.getString(1), sessionId);
        return owners.isEmpty() ? null : owners.get(0);
    }

    private String ownerOfSession(String sessionId) {
        List<String> owners = jdbcTemplate.query("SELECT owner_user_id FROM agent_session WHERE session_id = ?", (resultSet, rowNum) -> resultSet.getString(1), sessionId);
        return owners.isEmpty() ? null : owners.get(0);
    }

    private void execute(String statement) { jdbcTemplate.execute(statement); }
    private void tryExecute(String statement) { try { execute(statement); } catch (Exception ignored) { } }
    private String text(Map<String, Object> payload, String key) { Object value = payload.get(key); return value == null ? null : value.toString(); }
    private String toJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException error) { throw new IllegalStateException("Unable to serialize agent history payload", error); } }
    private Map<String, Object> fromJson(String value) { try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (JsonProcessingException error) { throw new IllegalStateException("Unable to deserialize agent history payload", error); } }

    private AgentEventType parseType(String value) {
        return switch (value) {
            case "run_started" -> AgentEventType.RUN_STARTED; case "plan" -> AgentEventType.PLAN; case "prompt_optimization" -> AgentEventType.PROMPT_OPTIMIZATION; case "task" -> AgentEventType.TASK; case "tool_call" -> AgentEventType.TOOL_CALL; case "tool_result" -> AgentEventType.TOOL_RESULT; case "image" -> AgentEventType.IMAGE; case "summary" -> AgentEventType.SUMMARY; case "run_completed" -> AgentEventType.RUN_COMPLETED; case "heartbeat" -> AgentEventType.HEARTBEAT; case "error" -> AgentEventType.ERROR; default -> throw new IllegalArgumentException("Unknown stored agent event type: " + value);
        };
    }

    private AgentEventStatus parseStatus(String value) {
        return switch (value) { case "queued" -> AgentEventStatus.QUEUED; case "running" -> AgentEventStatus.RUNNING; case "complete" -> AgentEventStatus.COMPLETE; case "failed" -> AgentEventStatus.FAILED; default -> throw new IllegalArgumentException("Unknown stored agent event status: " + value); };
    }
}
'@

Write-Source "src/main/java/com/jd/genie/service/agent/PersistentAgentWorkspaceCatalog.java" @'
package com.jd.genie.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentSessionOverview;
import com.jd.genie.model.agent.AgentWorkspaceAsset;
import com.jd.genie.model.agent.AgentWorkspaceSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** User-scoped workspace catalog over the isolated JendaAgent persistence database. */
@Component
@ConditionalOnExpression("'${agent.history.persistence:file}' == 'file' or '${agent.history.persistence:file}' == 'mysql'")
public class PersistentAgentWorkspaceCatalog {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PersistentAgentWorkspaceCatalog(ObjectMapper objectMapper, Environment environment) {
        String jdbcUrl = environment.getProperty("agent.history.jdbc-url", "jdbc:h2:file:./runtime/agent-history;MODE=MySQL");
        String username = environment.getProperty("agent.history.username", "sa");
        String password = environment.getProperty("agent.history.password", "");
        this.jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(jdbcUrl, username, password));
        this.objectMapper = objectMapper;
    }

    public List<AgentSessionOverview> list(String ownerUserId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query("SELECT s.session_id, s.latest_run_id, s.mode, s.latest_prompt, s.run_status, s.created_at, s.updated_at, (SELECT COUNT(*) FROM agent_run r WHERE r.session_id = s.session_id) AS run_count, (SELECT COUNT(*) FROM agent_message m WHERE m.session_id = s.session_id) AS event_count FROM agent_session s WHERE s.owner_user_id = ? ORDER BY s.updated_at DESC LIMIT ?", (resultSet, rowNum) -> toSession(resultSet), ownerUserId, safeLimit);
    }

    public Optional<AgentWorkspaceSnapshot> workspace(String ownerUserId, String sessionId) {
        List<AgentSessionOverview> sessions = jdbcTemplate.query("SELECT s.session_id, s.latest_run_id, s.mode, s.latest_prompt, s.run_status, s.created_at, s.updated_at, (SELECT COUNT(*) FROM agent_run r WHERE r.session_id = s.session_id) AS run_count, (SELECT COUNT(*) FROM agent_message m WHERE m.session_id = s.session_id) AS event_count FROM agent_session s WHERE s.session_id = ? AND s.owner_user_id = ?", (resultSet, rowNum) -> toSession(resultSet), sessionId, ownerUserId);
        if (sessions.isEmpty()) return Optional.empty();
        List<AgentWorkspaceAsset> assets = new ArrayList<>();
        jdbcTemplate.query("SELECT run_id, image_urls_json, created_at FROM agent_run WHERE session_id = ? AND owner_user_id = ? ORDER BY created_at DESC", (RowCallbackHandler) resultSet -> {
            String runId = resultSet.getString("run_id");
            Instant createdAt = instant(resultSet.getTimestamp("created_at"));
            List<String> imageUrls = imageUrls(resultSet.getString("image_urls_json"));
            for (int index = 0; index < imageUrls.size(); index++) assets.add(new AgentWorkspaceAsset("reference-" + runId + "-" + index, runId, "Reference " + (index + 1), imageUrls.get(index), "reference", createdAt));
        }, sessionId, ownerUserId);
        jdbcTemplate.query("SELECT m.run_id, m.occurred_at, i.asset_id, i.title, i.image_url FROM agent_image_message i JOIN agent_message m ON m.event_id = i.event_id JOIN agent_session s ON s.session_id = m.session_id WHERE m.session_id = ? AND s.owner_user_id = ? ORDER BY m.occurred_at DESC", (RowCallbackHandler) resultSet -> assets.add(new AgentWorkspaceAsset(resultSet.getString("asset_id"), resultSet.getString("run_id"), resultSet.getString("title"), resultSet.getString("image_url"), "generated", instant(resultSet.getTimestamp("occurred_at")))), sessionId, ownerUserId);
        assets.sort(Comparator.comparing(AgentWorkspaceAsset::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return Optional.of(new AgentWorkspaceSnapshot(sessions.get(0), assets));
    }

    private AgentSessionOverview toSession(java.sql.ResultSet resultSet) throws java.sql.SQLException { return new AgentSessionOverview(resultSet.getString("session_id"), resultSet.getString("latest_run_id"), resultSet.getString("mode"), resultSet.getString("latest_prompt"), resultSet.getString("run_status"), instant(resultSet.getTimestamp("created_at")), instant(resultSet.getTimestamp("updated_at")), resultSet.getInt("run_count"), resultSet.getInt("event_count")); }
    private List<String> imageUrls(String value) { try { return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<>() { }); } catch (Exception ignored) { return List.of(); } }
    private Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
'@

Write-Source "src/main/java/com/jd/genie/controller/AgentHistoryController.java" @'
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
'@

Write-Source "src/main/java/com/jd/genie/controller/AgentRunController.java" @'
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
'@

Write-Source "src/main/java/com/jd/genie/controller/DynamicPlanSolveController.java" @'
package com.jd.genie.controller;

import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.AgentRequestUserContext;
import com.jd.genie.service.agent.DynamicPlanSolveAgentRunService;
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
@RequestMapping("/api/v2/agent")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000"})
public class DynamicPlanSolveController {
    private final DynamicPlanSolveAgentRunService runService;

    @PostMapping(value = "/sessions/{sessionId}/runs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@PathVariable String sessionId, @Valid @RequestBody AgentRunRequest request, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {
        return AgentRequestUserContext.runAs(principal, () -> runService.startRun(sessionId, request));
    }
}
'@

Write-Source "src/main/java/com/jd/genie/controller/AgentWorkspaceController.java" @'
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
'@

Replace-Exactly "src/main/java/com/jd/genie/config/PersistentAgentHistoryStoreInjector.java" 'import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;' 'import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;'
Replace-Exactly "src/main/java/com/jd/genie/config/PersistentAgentHistoryStoreInjector.java" '@ConditionalOnProperty(name = "agent.history.persistence", havingValue = "file")' '@ConditionalOnExpression("''${agent.history.persistence:file}'' == ''file'' or ''${agent.history.persistence:file}'' == ''mysql''")'
Replace-Exactly "src/main/java/com/jd/genie/service/agent/PersistentAgentHistoryStore.java" 'import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;' 'import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;'
Replace-Exactly "src/main/java/com/jd/genie/service/agent/PersistentAgentHistoryStore.java" '@ConditionalOnProperty(name = "agent.history.persistence", havingValue = "file")' '@ConditionalOnExpression("''${agent.history.persistence:file}'' == ''file'' or ''${agent.history.persistence:file}'' == ''mysql''")'

Replace-Exactly "src/main/java/com/jd/genie/controller/AgentDirectUploadController.java" 'import com.jd.genie.model.agent.AgentImageUploadResponse;' 'import com.jd.genie.model.agent.AgentImageUploadResponse;`nimport com.jd.genie.model.auth.AgentPrincipal;`nimport com.jd.genie.service.agent.AgentHistoryStore;`nimport com.jd.genie.service.agent.AgentRequestUserContext;`nimport com.jd.genie.service.auth.AgentAuthenticationFilter;'
Replace-Exactly "src/main/java/com/jd/genie/controller/AgentDirectUploadController.java" 'import org.springframework.web.bind.annotation.RequestBody;' 'import org.springframework.web.bind.annotation.RequestBody;`nimport org.springframework.web.bind.annotation.RequestAttribute;'
Replace-Exactly "src/main/java/com/jd/genie/controller/AgentDirectUploadController.java" '    private final StsDirectUploadTicketService ticketService;' '    private final StsDirectUploadTicketService ticketService;`n    private final AgentHistoryStore historyStore;'
Replace-Exactly "src/main/java/com/jd/genie/controller/AgentDirectUploadController.java" '    public DirectUploadTicket issue(@Valid @RequestBody DirectUploadTicketRequest request) {`n        return ticketService.issue(request);`n    }`n`n    @PostMapping("/complete")`n    public AgentImageUploadResponse complete(@Valid @RequestBody DirectUploadCompleteRequest request) {`n        return ticketService.complete(request.getUploadId());`n    }' '    public DirectUploadTicket issue(@Valid @RequestBody DirectUploadTicketRequest request, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {`n        historyStore.claimSession(principal.userId(), request.getSessionId());`n        return AgentRequestUserContext.runAs(principal, () -> ticketService.issue(request));`n    }`n`n    @PostMapping("/complete")`n    public AgentImageUploadResponse complete(@Valid @RequestBody DirectUploadCompleteRequest request, @RequestAttribute(AgentAuthenticationFilter.PRINCIPAL_ATTRIBUTE) AgentPrincipal principal) {`n        return AgentRequestUserContext.runAs(principal, () -> ticketService.complete(request.getUploadId()));`n    }'

Replace-Exactly "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" 'tickets.put(uploadId, new TicketRecord(request.getFileName(), request.getMediaType(), request.getSize(), objectKey, expiresAt));' 'tickets.put(uploadId, new TicketRecord(AgentRequestUserContext.current().userId(), request.getSessionId(), request.getFileName(), request.getMediaType(), request.getSize(), objectKey, expiresAt));'
Replace-Exactly "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" '        if (record == null || record.expiresAt < Instant.now().getEpochSecond()) {' '        if (record == null || record.expiresAt < Instant.now().getEpochSecond()) {'
Replace-Exactly "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" '        long uploadedSize = headObject(record.objectKey);' '        if (!record.ownerUserId.equals(AgentRequestUserContext.current().userId())) { throw new AgentSessionAccessDeniedException(); }`n        long uploadedSize = headObject(record.objectKey);'
Replace-Exactly "src/main/java/com/jd/genie/service/agent/StsDirectUploadTicketService.java" '    private record TicketRecord(String fileName, String mediaType, long size, String objectKey, long expiresAt) {' '    private record TicketRecord(String ownerUserId, String sessionId, String fileName, String mediaType, long size, String objectKey, long expiresAt) {'

Write-Host "Step 27 backend changes completed. Compile before restarting the backend."
