package com.jd.genie.service.agent.plansolve;

import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.service.agent.AgentAssetMetadataStore;
import com.jd.genie.service.agent.AgentToolResult;
import com.jd.genie.service.agent.AgentToolType;
import com.jd.genie.service.agent.HttpAgentToolClient;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Observable and bounded DAG executor; every transition is stored before it is emitted. */
@Component
@RequiredArgsConstructor
public class ObservableDagTaskExecutor {
    private static final int MAX_CONCURRENCY = 3;

    private final PlanSolveExecutionStore store;
    private final HttpAgentToolClient toolClient;
    private final AgentAssetMetadataStore assetStore;
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "plan-solve-dag-worker");
        thread.setDaemon(true);
        return thread;
    });

    public ExecutionOutcome execute(ExecutionContext context, EventPublisher events) throws Exception {
        Map<String, PlanSolveExecutionStore.TaskSnapshot> states = new LinkedHashMap<>(store.taskStates(context.runId()));
        Map<String, PlanTaskSpec> tasks = context.plan().tasks().stream().collect(java.util.stream.Collectors.toMap(PlanTaskSpec::id, task -> task));
        while (true) {
            List<PlanTaskSpec> ready = new ArrayList<>();
            for (PlanTaskSpec task : context.plan().tasks()) {
                PlanSolveExecutionStore.TaskSnapshot snapshot = states.get(task.id());
                if (snapshot == null || snapshot.state() != PlanTaskState.PENDING) continue;
                String reason = skipReason(task, states, context);
                if (reason != null) {
                    transition(context, states, task, PlanTaskState.SKIPPED, snapshot.attempts(), Map.of("reason", reason));
                    events.publish(AgentEventType.TASK, AgentEventStatus.SKIPPED, "DagTaskExecutor", taskPayload(task, "skipped", reason, snapshot.attempts()));
                } else if (dependenciesComplete(task, states)) {
                    ready.add(task);
                }
            }
            if (ready.isEmpty()) {
                if (isTerminal(states)) return new ExecutionOutcome(false, states);
                for (PlanTaskSpec task : context.plan().tasks()) {
                    PlanSolveExecutionStore.TaskSnapshot snapshot = states.get(task.id());
                    if (snapshot.state() == PlanTaskState.PENDING) {
                        transition(context, states, task, PlanTaskState.SKIPPED, snapshot.attempts(), Map.of("reason", "blocked by dependency"));
                        events.publish(AgentEventType.TASK, AgentEventStatus.SKIPPED, "DagTaskExecutor", taskPayload(task, "skipped", "blocked by dependency", snapshot.attempts()));
                    }
                }
                continue;
            }
            ready.sort(Comparator.comparing(PlanTaskSpec::parallelGroup).thenComparing(PlanTaskSpec::id));
            for (PlanTaskSpec task : ready) {
                if (requiresHumanGate(task, states.get(task.id()))) {
                    String message = task.confirmationMessage().isBlank() ? "Confirm this task before execution: " + task.title() : task.confirmationMessage();
                    String approvalId = store.requestApproval(context.runId(), task.id(), context.ownerUserId(), message);
                    PlanSolveExecutionStore.TaskSnapshot snapshot = states.get(task.id());
                    transition(context, states, task, PlanTaskState.WAITING_CONFIRMATION, snapshot.attempts(), Map.of("approvalId", approvalId, "message", message));
                    store.updateRunStatus(context.runId(), "WAITING_CONFIRMATION");
                    events.publish(AgentEventType.CONFIRMATION_REQUIRED, AgentEventStatus.WAITING_CONFIRMATION, "HumanConfirmation", Map.of(
                            "taskId", task.id(), "approvalId", approvalId, "title", task.title(), "content", message,
                            "parallelGroup", task.parallelGroup(), "actions", List.of("approve", "reject")));
                    return new ExecutionOutcome(true, states);
                }
            }
            executeBatch(context, states, ready, events);
        }
    }

    private void executeBatch(ExecutionContext context, Map<String, PlanSolveExecutionStore.TaskSnapshot> states,
                              List<PlanTaskSpec> ready, EventPublisher events) throws Exception {
        ExecutorCompletionService<TaskExecution> completion = new ExecutorCompletionService<>(workers);
        int submitted = 0;
        for (PlanTaskSpec task : ready) {
            PlanSolveExecutionStore.TaskSnapshot snapshot = states.get(task.id());
            if (snapshot.state() != PlanTaskState.PENDING || requiresHumanGate(task, snapshot)) continue;
            int attempt = snapshot.attempts() + 1;
            transition(context, states, task, PlanTaskState.RUNNING, attempt, Map.of());
            events.publish(AgentEventType.TASK, AgentEventStatus.RUNNING, "DagTaskExecutor", taskPayload(task, "running", "submitted to observable executor", attempt));
            events.publish(AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING, "ToolRouter", Map.of("taskId", task.id(), "tool", task.kind().name(), "title", task.title(), "content", "tool call started", "attempt", attempt, "parallelGroup", task.parallelGroup()));
            completion.submit(callable(context, task, attempt));
            submitted++;
        }
        for (int index = 0; index < submitted; index++) {
            Future<TaskExecution> future = completion.take();
            TaskExecution execution = future.get();
            PlanTaskSpec task = execution.task();
            if (execution.result().success()) {
                Map<String, Object> result = Map.of("summary", execution.result().summary(), "provider", safe(execution.result().provider()), "imageUrl", safe(execution.result().imageUrl()));
                transition(context, states, task, PlanTaskState.COMPLETE, execution.attempt(), result);
                events.publish(AgentEventType.TOOL_RESULT, AgentEventStatus.COMPLETE, "ToolRouter", Map.of("taskId", task.id(), "tool", execution.result().tool().name(), "title", task.title(), "content", execution.result().summary(), "attempt", execution.attempt(), "parallelGroup", task.parallelGroup()));
                publishImage(context, task, execution.result(), events);
            } else if (execution.attempt() < task.maxAttempts()) {
                transition(context, states, task, PlanTaskState.PENDING, execution.attempt(), Map.of("lastError", execution.result().summary()));
                events.publish(AgentEventType.TASK, AgentEventStatus.QUEUED, "DagTaskExecutor", taskPayload(task, "retry", execution.result().summary(), execution.attempt()));
            } else {
                transition(context, states, task, PlanTaskState.FAILED, execution.attempt(), Map.of("error", execution.result().summary()));
                events.publish(AgentEventType.TOOL_RESULT, AgentEventStatus.FAILED, "ToolRouter", Map.of("taskId", task.id(), "tool", execution.result().tool().name(), "title", task.title(), "content", execution.result().summary(), "attempt", execution.attempt(), "parallelGroup", task.parallelGroup()));
            }
        }
    }

    private Callable<TaskExecution> callable(ExecutionContext context, PlanTaskSpec task, int attempt) {
        return () -> {
            AgentToolType type = AgentToolType.valueOf(task.kind().name());
            String prompt = task.prompt().isBlank() ? context.request().getPrompt() : task.prompt();
            return new TaskExecution(task, attempt, toolClient.execute(type, prompt, context.request().getImageUrls(), context.request().getImageProvider(), context.ownerUserId()));
        };
    }

    private void publishImage(ExecutionContext context, PlanTaskSpec task, AgentToolResult result, EventPublisher events) throws Exception {
        if (result.imageUrl() == null || result.imageUrl().isBlank()) return;
        String assetId = "asset-" + UUID.randomUUID();
        String title = task.title() + " output";
        assetStore.recordGeneratedForSession(context.sessionId(), context.runId(), assetId, title, result.imageUrl());
        events.publish(AgentEventType.IMAGE, AgentEventStatus.COMPLETE, "ImageToolchain", Map.of("assetId", assetId, "taskId", task.id(), "title", title, "imageUrl", result.imageUrl(), "content", result.summary(), "sourceTool", result.tool().name()));
    }

    private boolean requiresHumanGate(PlanTaskSpec task, PlanSolveExecutionStore.TaskSnapshot snapshot) {
        if (task.kind() == PlanTaskKind.HUMAN_CONFIRMATION) return true;
        return task.requiresConfirmation() && !Boolean.TRUE.equals(snapshot.result().get("approved"));
    }
    private String skipReason(PlanTaskSpec task, Map<String, PlanSolveExecutionStore.TaskSnapshot> states, ExecutionContext context) {
        if ("no_input_image".equals(task.skipWhen()) && context.request().getImageUrls().isEmpty()) return "no input image";
        if ("previous_failed".equals(task.skipWhen()) && states.values().stream().anyMatch(value -> value.state() == PlanTaskState.FAILED)) return "a previous task failed";
        if ("tool_unavailable".equals(task.skipWhen()) && task.kind() != PlanTaskKind.HUMAN_CONFIRMATION && !toolClient.isConfigured(AgentToolType.valueOf(task.kind().name()))) return "tool is not configured";
        for (String dependency : task.dependsOn()) {
            PlanTaskState state = states.get(dependency).state();
            if (state == PlanTaskState.FAILED || state == PlanTaskState.SKIPPED) return "dependency " + dependency + " did not complete";
        }
        return null;
    }

    private boolean dependenciesComplete(PlanTaskSpec task, Map<String, PlanSolveExecutionStore.TaskSnapshot> states) {
        return task.dependsOn().stream().allMatch(id -> states.get(id).state() == PlanTaskState.COMPLETE);
    }

    private boolean isTerminal(Map<String, PlanSolveExecutionStore.TaskSnapshot> states) {
        return states.values().stream().allMatch(snapshot -> snapshot.state() == PlanTaskState.COMPLETE || snapshot.state() == PlanTaskState.FAILED || snapshot.state() == PlanTaskState.SKIPPED);
    }

    private void transition(ExecutionContext context, Map<String, PlanSolveExecutionStore.TaskSnapshot> states, PlanTaskSpec task, PlanTaskState state, int attempts, Map<String, Object> result) {
        store.saveTask(context.runId(), task.id(), state, attempts, result);
        states.put(task.id(), new PlanSolveExecutionStore.TaskSnapshot(state, attempts, result));
    }

    private Map<String, Object> taskPayload(PlanTaskSpec task, String state, String content, int attempt) {
        return Map.of("taskId", task.id(), "title", task.title(), "content", content, "state", state, "attempt", attempt, "maxAttempts", task.maxAttempts(), "dependsOn", task.dependsOn(), "parallelGroup", task.parallelGroup(), "skipWhen", task.skipWhen());
    }

    private String safe(String value) { return value == null ? "" : value; }

    @PreDestroy
    void close() { workers.shutdown(); }

    public record ExecutionContext(String sessionId, String runId, String ownerUserId, com.jd.genie.model.agent.AgentRunRequest request, StructuredAgentPlan plan) { }
    public record ExecutionOutcome(boolean awaitingConfirmation, Map<String, PlanSolveExecutionStore.TaskSnapshot> states) { }
    private record TaskExecution(PlanTaskSpec task, int attempt, AgentToolResult result) { }
    @FunctionalInterface public interface EventPublisher { void publish(AgentEventType type, AgentEventStatus status, String agent, Map<String, Object> payload) throws Exception; }
}