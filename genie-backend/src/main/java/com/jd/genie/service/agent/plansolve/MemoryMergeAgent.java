package com.jd.genie.service.agent.plansolve;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

/** Rule-based merge avoids invented facts before SummaryAgent writes a delivery. */
@Component
@RequiredArgsConstructor
public class MemoryMergeAgent {
    private final SharedTaskMemoryStore memoryStore;

    public MergedTaskContext merge(String runId) {
        List<SharedTaskMemoryItem> tasks = memoryStore.list(runId);
        LinkedHashSet<String> assets = new LinkedHashSet<>();
        List<String> completed = new java.util.ArrayList<>();
        List<String> failures = new java.util.ArrayList<>();
        for (SharedTaskMemoryItem task : tasks) {
            assets.addAll(task.assetIds());
            if ("COMPLETE".equals(task.state()) && !task.summary().isBlank()) completed.add(task.taskId() + ": " + task.summary());
            if (("FAILED".equals(task.state()) || "SKIPPED".equals(task.state())) && !task.failureReason().isBlank()) failures.add(task.taskId() + ": " + task.failureReason());
        }
        return new MergedTaskContext(runId, tasks, List.copyOf(assets), completed, failures);
    }
}