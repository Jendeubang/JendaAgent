package com.jd.genie.service.agent.plansolve;

import java.util.List;

/** The only task-level source SummaryAgent may consume in Plan-Solve mode. */
public record MergedTaskContext(String runId, List<SharedTaskMemoryItem> tasks, List<String> assetIds,
                                List<String> completedSummaries, List<String> failures) {
    public MergedTaskContext {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        assetIds = assetIds == null ? List.of() : List.copyOf(assetIds);
        completedSummaries = completedSummaries == null ? List.of() : List.copyOf(completedSummaries);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public String summaryInput() {
        return "completed=" + completedSummaries + "\nfailures=" + failures + "\nassetIds=" + assetIds + "\ntraceableTasks=" + tasks.stream().map(SharedTaskMemoryItem::taskId).toList();
    }
}