package com.jd.genie.service.agent.plansolve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentRunRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable state required to pause and resume a structured DAG safely. */
@Repository
@RequiredArgsConstructor
public class PlanSolveExecutionStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void initializeSchema() {
        execute("CREATE TABLE IF NOT EXISTS agent_plan_execution (run_id VARCHAR(64) PRIMARY KEY, session_id VARCHAR(64) NOT NULL, owner_user_id VARCHAR(64) NOT NULL, plan_json TEXT NOT NULL, request_json TEXT NOT NULL, status VARCHAR(40) NOT NULL, created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS agent_plan_task_state (run_id VARCHAR(64) NOT NULL, task_id VARCHAR(64) NOT NULL, state VARCHAR(40) NOT NULL, attempts INT NOT NULL, result_json TEXT, updated_at TIMESTAMP NOT NULL, PRIMARY KEY (run_id, task_id))");
        execute("CREATE TABLE IF NOT EXISTS agent_plan_approval (approval_id VARCHAR(64) PRIMARY KEY, run_id VARCHAR(64) NOT NULL, task_id VARCHAR(64) NOT NULL, owner_user_id VARCHAR(64) NOT NULL, message TEXT NOT NULL, status VARCHAR(32) NOT NULL, note TEXT, created_at TIMESTAMP NOT NULL, resolved_at TIMESTAMP)");
    }

    public void create(String sessionId, String runId, String ownerUserId, AgentRunRequest request, StructuredAgentPlan plan) {
        Instant now = Instant.now();
        jdbcTemplate.update("INSERT INTO agent_plan_execution (run_id, session_id, owner_user_id, plan_json, request_json, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                runId, sessionId, ownerUserId, json(plan), json(request), "RUNNING", Timestamp.from(now), Timestamp.from(now));
        for (PlanTaskSpec task : plan.tasks()) {
            jdbcTemplate.update("INSERT INTO agent_plan_task_state (run_id, task_id, state, attempts, updated_at) VALUES (?, ?, ?, ?, ?)",
                    runId, task.id(), PlanTaskState.PENDING.name(), 0, Timestamp.from(now));
        }
    }

    public StoredExecution load(String ownerUserId, String sessionId, String runId) {
        List<StoredExecution> rows = jdbcTemplate.query("SELECT plan_json, request_json, status FROM agent_plan_execution WHERE run_id = ? AND session_id = ? AND owner_user_id = ?",
                (rs, index) -> new StoredExecution(sessionId, runId, ownerUserId, read(rs.getString("request_json"), AgentRunRequest.class), read(rs.getString("plan_json"), StructuredAgentPlan.class), rs.getString("status")), runId, sessionId, ownerUserId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Plan-Solve run was not found for the current user");
        return rows.get(0);
    }

    public Map<String, TaskSnapshot> taskStates(String runId) {
        Map<String, TaskSnapshot> states = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT task_id, state, attempts, result_json FROM agent_plan_task_state WHERE run_id = ?", (RowCallbackHandler) rs ->
                states.put(rs.getString("task_id"), new TaskSnapshot(PlanTaskState.valueOf(rs.getString("state")), rs.getInt("attempts"), map(rs.getString("result_json")))), runId);
        return states;
    }

    public void saveTask(String runId, String taskId, PlanTaskState state, int attempts, Map<String, Object> result) {
        jdbcTemplate.update("UPDATE agent_plan_task_state SET state = ?, attempts = ?, result_json = ?, updated_at = ? WHERE run_id = ? AND task_id = ?",
                state.name(), attempts, result == null ? null : json(result), Timestamp.from(Instant.now()), runId, taskId);
    }

    public String requestApproval(String runId, String taskId, String ownerUserId, String message) {
        List<String> existing = jdbcTemplate.query("SELECT approval_id FROM agent_plan_approval WHERE run_id = ? AND task_id = ? AND status = 'WAITING'", (rs, index) -> rs.getString(1), runId, taskId);
        if (!existing.isEmpty()) return existing.get(0);
        String approvalId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO agent_plan_approval (approval_id, run_id, task_id, owner_user_id, message, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                approvalId, runId, taskId, ownerUserId, message, "WAITING", Timestamp.from(Instant.now()));
        return approvalId;
    }

    public Approval resolveApproval(String ownerUserId, String runId, String approvalId, boolean approved, String note) {
        List<Approval> approvals = jdbcTemplate.query("SELECT task_id, message, status FROM agent_plan_approval WHERE approval_id = ? AND run_id = ? AND owner_user_id = ?",
                (rs, index) -> new Approval(approvalId, rs.getString("task_id"), rs.getString("message"), rs.getString("status")), approvalId, runId, ownerUserId);
        if (approvals.isEmpty()) throw new IllegalArgumentException("Approval was not found for the current user");
        Approval approval = approvals.get(0);
        if (!"WAITING".equals(approval.status())) throw new IllegalStateException("Approval has already been resolved");
        jdbcTemplate.update("UPDATE agent_plan_approval SET status = ?, note = ?, resolved_at = ? WHERE approval_id = ? AND owner_user_id = ?",
                approved ? "APPROVED" : "REJECTED", note, Timestamp.from(Instant.now()), approvalId, ownerUserId);
        return approval;
    }

    public void updateRunStatus(String runId, String status) {
        jdbcTemplate.update("UPDATE agent_plan_execution SET status = ?, updated_at = ? WHERE run_id = ?", status, Timestamp.from(Instant.now()), runId);
    }

    private void execute(String sql) { jdbcTemplate.execute(sql); }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Could not persist Plan-Solve state", error); } }
    private <T> T read(String value, Class<T> type) { try { return objectMapper.readValue(value, type); } catch (Exception error) { throw new IllegalStateException("Could not restore Plan-Solve state", error); } }
    private Map<String, Object> map(String value) { if (value == null || value.isBlank()) return Map.of(); try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return Map.of("restoreError", error.getMessage()); } }

    public record StoredExecution(String sessionId, String runId, String ownerUserId, AgentRunRequest request, StructuredAgentPlan plan, String status) { }
    public record TaskSnapshot(PlanTaskState state, int attempts, Map<String, Object> result) { }
    public record Approval(String approvalId, String taskId, String message, String status) { }
}
