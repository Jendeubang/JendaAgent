package com.jd.genie.service.agent.plansolve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.persistence.agent.entity.AgentPlanApprovalEntity;
import com.jd.genie.persistence.agent.entity.AgentPlanExecutionEntity;
import com.jd.genie.persistence.agent.entity.AgentPlanTaskStateEntity;
import com.jd.genie.persistence.agent.service.AgentPlanPersistenceService;
import lombok.RequiredArgsConstructor;
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
    private final AgentPlanPersistenceService persistence;
    private final ObjectMapper objectMapper;

    public void create(String sessionId, String runId, String ownerUserId, AgentRunRequest request, StructuredAgentPlan plan) {
        Timestamp timestamp = Timestamp.from(Instant.now());
        AgentPlanExecutionEntity execution = new AgentPlanExecutionEntity();
        execution.setRunId(runId);
        execution.setSessionId(sessionId);
        execution.setOwnerUserId(ownerUserId);
        execution.setPlanJson(json(plan));
        execution.setRequestJson(json(request));
        execution.setStatus("RUNNING");
        execution.setCreatedAt(timestamp);
        execution.setUpdatedAt(timestamp);
        List<AgentPlanTaskStateEntity> tasks = plan.tasks().stream().map(task -> {
            AgentPlanTaskStateEntity state = new AgentPlanTaskStateEntity();
            state.setRunId(runId);
            state.setTaskId(task.id());
            state.setState(PlanTaskState.PENDING.name());
            state.setAttempts(0);
            state.setUpdatedAt(timestamp);
            return state;
        }).toList();
        persistence.create(execution, tasks);
    }

    public StoredExecution load(String ownerUserId, String sessionId, String runId) {
        AgentPlanExecutionEntity execution = persistence.load(ownerUserId, sessionId, runId);
        if (execution == null) throw new IllegalArgumentException("Plan-Solve run was not found for the current user");
        return new StoredExecution(sessionId, runId, ownerUserId, read(execution.getRequestJson(), AgentRunRequest.class),
                read(execution.getPlanJson(), StructuredAgentPlan.class), execution.getStatus());
    }

    public Map<String, TaskSnapshot> taskStates(String runId) {
        Map<String, TaskSnapshot> states = new LinkedHashMap<>();
        persistence.taskStates(runId).forEach(task -> states.put(task.getTaskId(),
                new TaskSnapshot(PlanTaskState.valueOf(task.getState()), task.getAttempts(), map(task.getResultJson()))));
        return states;
    }

    public void saveTask(String runId, String taskId, PlanTaskState state, int attempts, Map<String, Object> result) {
        persistence.saveTask(runId, taskId, state.name(), attempts, result == null ? null : json(result));
    }

    public String requestApproval(String runId, String taskId, String ownerUserId, String message) {
        return persistence.requestApproval(runId, taskId, ownerUserId, message, UUID.randomUUID().toString());
    }

    public Approval resolveApproval(String ownerUserId, String runId, String approvalId, boolean approved, String note) {
        AgentPlanApprovalEntity entity = persistence.findApproval(ownerUserId, runId, approvalId);
        if (entity == null) throw new IllegalArgumentException("Approval was not found for the current user");
        if (!"WAITING".equals(entity.getStatus())) throw new IllegalStateException("Approval has already been resolved");
        persistence.resolveApproval(ownerUserId, approvalId, approved, note);
        return new Approval(approvalId, entity.getTaskId(), entity.getMessage(), entity.getStatus());
    }

    public void updateRunStatus(String runId, String status) { persistence.updateRunStatus(runId, status); }

    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Could not persist Plan-Solve state", error); } }
    private <T> T read(String value, Class<T> type) { try { return objectMapper.readValue(value, type); } catch (Exception error) { throw new IllegalStateException("Could not restore Plan-Solve state", error); } }
    private Map<String, Object> map(String value) { if (value == null || value.isBlank()) return Map.of(); try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return Map.of("restoreError", error.getMessage()); } }

    public record StoredExecution(String sessionId, String runId, String ownerUserId, AgentRunRequest request, StructuredAgentPlan plan, String status) { }
    public record TaskSnapshot(PlanTaskState state, int attempts, Map<String, Object> result) { }
    public record Approval(String approvalId, String taskId, String message, String status) { }
}