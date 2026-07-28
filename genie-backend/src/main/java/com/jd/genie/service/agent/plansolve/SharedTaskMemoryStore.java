package com.jd.genie.service.agent.plansolve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.persistence.agent.entity.AgentSharedTaskMemoryEntity;
import com.jd.genie.persistence.agent.mapper.AgentSharedTaskMemoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Shared task memory is persisted independently from scheduler state for replay and merge. */
@Repository
@RequiredArgsConstructor
public class SharedTaskMemoryStore {
    private final AgentSharedTaskMemoryMapper mapper;
    private final ObjectMapper objectMapper;

    public void started(String runId, String taskId, String tool, int attempt, Map<String, Object> input) {
        save(runId, taskId, tool, "RUNNING", attempt, input, Map.of(), List.of(), "", "");
    }

    public void completed(String runId, String taskId, String tool, int attempt, Map<String, Object> input,
                          Map<String, Object> output, List<String> assetIds, String summary) {
        save(runId, taskId, tool, "COMPLETE", attempt, input, output, assetIds, "", summary);
    }

    public void failed(String runId, String taskId, String tool, int attempt, Map<String, Object> input, String failureReason) {
        save(runId, taskId, tool, "FAILED", attempt, input, Map.of(), List.of(), failureReason, failureReason);
    }

    public void skipped(String runId, String taskId, String tool, int attempt, Map<String, Object> input, String reason) {
        save(runId, taskId, tool, "SKIPPED", attempt, input, Map.of(), List.of(), reason, reason);
    }

    public List<SharedTaskMemoryItem> list(String runId) {
        return mapper.findByRun(runId).stream().map(item -> new SharedTaskMemoryItem(item.getTaskId(), item.getToolName(), item.getState(), item.getAttempt(),
                map(item.getInputJson()), map(item.getOutputJson()), assetIds(item.getAssetIdsJson()), item.getFailureReason(), item.getSummary())).toList();
    }

    private void save(String runId, String taskId, String tool, String state, int attempt, Map<String, Object> input,
                      Map<String, Object> output, List<String> assetIds, String failureReason, String summary) {
        AgentSharedTaskMemoryEntity entity = new AgentSharedTaskMemoryEntity();
        entity.setRunId(runId);
        entity.setTaskId(taskId);
        entity.setToolName(tool);
        entity.setState(state);
        entity.setAttempt(attempt);
        entity.setInputJson(json(input));
        entity.setOutputJson(json(output));
        entity.setAssetIdsJson(json(assetIds));
        entity.setFailureReason(failureReason);
        entity.setSummary(summary);
        entity.setUpdatedAt(Timestamp.from(Instant.now()));
        mapper.upsert(entity);
    }

    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception error) { throw new IllegalStateException("Could not persist shared task memory", error); } }
    private Map<String, Object> map(String value) { if (value == null || value.isBlank()) return Map.of(); try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return Map.of("restoreError", error.getMessage()); } }
    private List<String> assetIds(String value) { if (value == null || value.isBlank()) return List.of(); try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (Exception error) { return List.of(); } }
}