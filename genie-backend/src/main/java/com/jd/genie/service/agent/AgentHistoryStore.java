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
