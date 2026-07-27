package com.jd.genie.service.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.persistence.agent.entity.AgentEventEntity;
import com.jd.genie.persistence.agent.service.AgentOperationalPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Compatibility facade for agent history. SQL ownership lives in MyBatis-Flex
 * mappers and schema ownership lives in Flyway migrations.
 */
@Repository
@RequiredArgsConstructor
public class AgentHistoryStore {
    private final AgentOperationalPersistenceService persistence;
    private final ObjectMapper objectMapper;

    public void claimSession(String ownerUserId, String sessionId) { persistence.claimSession(ownerUserId, sessionId); }
    public boolean canAccess(String ownerUserId, String sessionId) { return persistence.canAccess(ownerUserId, sessionId); }
    public void startRun(String sessionId, String runId, AgentRunRequest request) { persistence.startRun(sessionId, runId, request); }
    public void append(AgentEvent event) { persistence.append(event); }
    public void completeRun(String sessionId, String runId, AgentEventStatus status) { persistence.completeRun(sessionId, runId, status); }
    public void updateRunStatus(String sessionId, String runId, AgentEventStatus status) { persistence.updateRunStatus(sessionId, runId, status); }
    public long lastSequence(String runId) { return persistence.lastSequence(runId); }

    public List<AgentEvent> replay(String sessionId) {
        String ownerUserId = AgentRequestUserContext.current().userId();
        return persistence.replay(ownerUserId, sessionId).stream().map(this::event).toList();
    }

    private AgentEvent event(AgentEventEntity entity) {
        return new AgentEvent("v1", entity.getEventId(), entity.getSessionId(), entity.getRunId(), entity.getSequenceNo(),
                parseType(entity.getEventType()), parseStatus(entity.getStatus()), entity.getAgentName(),
                entity.getOccurredAt().toInstant(), fromJson(entity.getPayloadJson()));
    }

    private Map<String, Object> fromJson(String value) {
        try { return objectMapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception error) { throw new IllegalStateException("Unable to deserialize agent event payload", error); }
    }

    private AgentEventType parseType(String value) {
        return switch (value) {
            case "run_started" -> AgentEventType.RUN_STARTED;
            case "plan" -> AgentEventType.PLAN;
            case "prompt_optimization" -> AgentEventType.PROMPT_OPTIMIZATION;
            case "task" -> AgentEventType.TASK;
            case "tool_call" -> AgentEventType.TOOL_CALL;
            case "tool_result" -> AgentEventType.TOOL_RESULT;
            case "image" -> AgentEventType.IMAGE;
            case "summary" -> AgentEventType.SUMMARY;
            case "confirmation_required" -> AgentEventType.CONFIRMATION_REQUIRED;
            case "react_think" -> AgentEventType.REACT_THINK;
            case "react_act" -> AgentEventType.REACT_ACT;
            case "react_observation" -> AgentEventType.REACT_OBSERVATION;
            case "react_decision" -> AgentEventType.REACT_DECISION;
            case "react_terminated" -> AgentEventType.REACT_TERMINATED;
            case "run_completed" -> AgentEventType.RUN_COMPLETED;
            case "heartbeat" -> AgentEventType.HEARTBEAT;
            case "error" -> AgentEventType.ERROR;
            default -> throw new IllegalArgumentException("Unknown stored agent event type: " + value);
        };
    }

    private AgentEventStatus parseStatus(String value) {
        return switch (value) {
            case "queued" -> AgentEventStatus.QUEUED;
            case "running" -> AgentEventStatus.RUNNING;
            case "complete" -> AgentEventStatus.COMPLETE;
            case "failed" -> AgentEventStatus.FAILED;
            case "skipped" -> AgentEventStatus.SKIPPED;
            case "waiting_confirmation" -> AgentEventStatus.WAITING_CONFIRMATION;
            default -> throw new IllegalArgumentException("Unknown stored agent event status: " + value);
        };
    }
}