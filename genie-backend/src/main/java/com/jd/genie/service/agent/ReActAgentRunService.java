package com.jd.genie.service.agent;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.config.AgentReActProperties;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.react.ReActAction;
import com.jd.genie.service.agent.react.ReActDecision;
import com.jd.genie.service.agent.react.ReActDecisionGenerator;
import com.jd.genie.service.agent.react.ReActRunControl;
import com.jd.genie.service.agent.react.ReActToolInvocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded Think -> Act -> Observation runtime with explicit independent-call parallelism. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActAgentRunService {
    private final AgentHistoryStore historyStore;
    private final ReActDecisionGenerator decisionGenerator;
    private final HttpAgentToolClient toolClient;
    private final AgentAssetMetadataStore assetStore;
    private final OpenAiCompatibleChatClient chatClient;
    private final AgentReActProperties properties;
    private final PromptOpAgent promptOpAgent;
    private final ReActRunControl runControl;
    private final ExecutorService parallelWorkers = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "react-controlled-worker");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        AgentPrincipal owner = AgentRequestUserContext.current();
        AtomicBoolean cancelled = runControl.register(runId, owner.userId());
        historyStore.startRun(sessionId, runId, request);
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(error -> cancelled.set(true));
        ThreadUtil.execute(() -> execute(emitter, sessionId, runId, request, owner, new AtomicLong(), cancelled));
        return emitter;
    }

    public boolean cancel(String runId, AgentPrincipal owner) {
        return runControl.cancel(runId, owner.userId());
    }

    private void execute(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request, AgentPrincipal owner,
                         AtomicLong sequence, AtomicBoolean cancelled) {
        Instant deadline = Instant.now().plus(safeTimeout(properties.getTimeout()));
        int maxSteps = Math.max(1, Math.min(properties.getMaxSteps(), 12));
        int repeatLimit = Math.max(1, Math.min(properties.getRepeatedToolLimit(), 5));
        ToolBudget budget = new ToolBudget(Math.max(1, properties.getMaxToolCalls()), Math.max(1, properties.getMaxCostUnits()));
        List<String> observations = new ArrayList<>();
        Map<String, Integer> fingerprintCounts = new HashMap<>();
        try {
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING, "ReActOrchestrator", Map.of(
                    "mode", "react-controlled-v2", "prompt", request.getPrompt(), "imageUrls", request.getImageUrls(), "maxSteps", maxSteps,
                    "timeoutSeconds", safeTimeout(properties.getTimeout()).toSeconds(), "repeatToolLimit", repeatLimit,
                    "maxToolCalls", budget.maxCalls, "maxCostUnits", budget.maxCostUnits, "maxParallelActions", Math.max(1, properties.getMaxParallelActions()))));
            for (int round = 1; round <= maxSteps; round++) {
                if (cancelled.get()) { terminate(emitter, sessionId, runId, sequence, "cancelled", round, observations); return; }
                if (Instant.now().isAfter(deadline)) { terminate(emitter, sessionId, runId, sequence, "timeout", round, observations); return; }
                ReActDecisionGenerator.GeneratedDecision generated = decisionGenerator.decide(request, round, String.join("\n", observations));
                ReActDecision decision = generated.decision();
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_THINK, AgentEventStatus.COMPLETE, "ReActAgent", Map.of(
                        "round", round, "title", "Think", "content", decision.reasoning(), "reasoningSummary", decision.reasoning(),
                        "modelGenerated", generated.modelGenerated(), "modelProvider", generated.provider(), "priorObservations", List.copyOf(observations))));
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_DECISION, AgentEventStatus.COMPLETE, "ReActAgent", Map.of(
                        "round", round, "title", "Next action", "content", decision.nextDecision(), "action", decision.action().name(),
                        "toolInput", decision.toolInput(), "imageProvider", decision.imageProvider(), "parallelActions", decision.parallelActions(), "nextDecision", decision.nextDecision())));
                if (decision.action() == ReActAction.FINISH) { finish(emitter, sessionId, runId, sequence, request, observations, false); return; }
                if (decision.action() == ReActAction.PLAN_SOLVE) { handoffToPlanSolve(emitter, sessionId, runId, sequence, round, observations, decision.reasoning()); return; }

                List<ReActToolInvocation> requested = decision.action() == ReActAction.PARALLEL ? decision.parallelActions()
                        : List.of(new ReActToolInvocation(decision.action(), decision.toolInput(), decision.imageProvider()));
                if (requested.size() > Math.max(1, properties.getMaxParallelActions())) { terminate(emitter, sessionId, runId, sequence, "parallel action limit exceeded", round, observations); return; }
                List<PreparedInvocation> prepared = prepare(request, requested, round, emitter, sessionId, runId, sequence, fingerprintCounts, repeatLimit);
                if (prepared == null) { terminate(emitter, sessionId, runId, sequence, "repeated tool call protection", round, observations); return; }
                if (!budget.reserve(prepared)) { terminate(emitter, sessionId, runId, sequence, "tool budget or cost budget exceeded", round, observations); return; }
                boolean parallel = prepared.size() > 1;
                publishCalls(emitter, sessionId, runId, sequence, round, prepared, parallel);
                List<InvocationResult> results = invoke(prepared, request, owner, cancelled, deadline);
                if (cancelled.get()) { terminate(emitter, sessionId, runId, sequence, "cancelled", round, observations); return; }
                for (InvocationResult result : results) {
                    publishResult(emitter, sessionId, runId, sequence, round, result, decision.nextDecision(), observations, parallel);
                }
            }
            terminate(emitter, sessionId, runId, sequence, "maximum step limit reached", maxSteps, observations);
        } catch (Exception error) {
            log.error("ReAct run {} failed", runId, error);
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            try { publish(emitter, event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED, "ReActOrchestrator", Map.of("code", "REACT_RUN_FAILED", "message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()))); } catch (IOException ignored) { }
            emitter.completeWithError(error);
        } finally {
            runControl.complete(runId);
        }
    }

    private List<PreparedInvocation> prepare(AgentRunRequest request, List<ReActToolInvocation> requested, int round, SseEmitter emitter,
                                              String sessionId, String runId, AtomicLong sequence, Map<String, Integer> fingerprints, int repeatLimit) throws IOException {
        List<PreparedInvocation> prepared = new ArrayList<>();
        for (ReActToolInvocation invocation : requested) {
            AgentToolType tool = AgentToolType.valueOf(invocation.action().name());
            String toolInput = invocation.toolInput();
            if (tool == AgentToolType.IMAGE_GENERATE || tool == AgentToolType.IMAGE_EDIT) {
                PromptOptimization optimization = promptOpAgent.optimize(toolInput, List.of(tool), request.getImageUrls(), request.isPromptOptimizationEnabled());
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.PROMPT_OPTIMIZATION,
                        optimization.applied() ? AgentEventStatus.COMPLETE : AgentEventStatus.SKIPPED, "PromptOpAgent", PromptOptimizationEventPayload.from(optimization, List.of(tool))));
                toolInput = optimization.optimizedPrompt();
            }
            String provider = invocation.imageProvider().isBlank() ? request.getImageProvider() : invocation.imageProvider();
            String fingerprint = fingerprint(invocation.action(), toolInput, request.getImageUrls(), provider);
            int attempt = fingerprints.merge(fingerprint, 1, Integer::sum);
            if (attempt > repeatLimit) return null;
            prepared.add(new PreparedInvocation(invocation.action(), tool, toolInput, provider, attempt, fingerprint));
        }
        return prepared;
    }

    private List<InvocationResult> invoke(List<PreparedInvocation> prepared, AgentRunRequest request, AgentPrincipal owner,
                                          AtomicBoolean cancelled, Instant deadline) throws Exception {
        ExecutorCompletionService<InvocationResult> completion = new ExecutorCompletionService<>(parallelWorkers);
        List<Future<InvocationResult>> futures = new ArrayList<>();
        for (PreparedInvocation call : prepared) {
            futures.add(completion.submit(invocationCallable(call, request, owner)));
        }
        List<InvocationResult> results = new ArrayList<>();
        while (results.size() < futures.size()) {
            if (cancelled.get()) { futures.forEach(future -> future.cancel(true)); return results; }
            long remaining = Duration.between(Instant.now(), deadline).toMillis();
            if (remaining <= 0) { futures.forEach(future -> future.cancel(true)); throw new IllegalStateException("ReAct deadline exceeded"); }
            Future<InvocationResult> future = completion.poll(Math.min(remaining, 250), TimeUnit.MILLISECONDS);
            if (future != null) results.add(future.get());
        }
        return results;
    }

    private Callable<InvocationResult> invocationCallable(PreparedInvocation call, AgentRunRequest request, AgentPrincipal owner) {
        return () -> new InvocationResult(call, toolClient.execute(call.tool(), call.toolInput(), request.getImageUrls(), call.imageProvider(), owner.userId()));
    }

    private void publishCalls(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, int round,
                              List<PreparedInvocation> calls, boolean parallel) throws IOException {
        for (PreparedInvocation call : calls) {
            String taskId = "react-" + round + "-" + call.tool().name().toLowerCase();
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING, "ToolRouter", Map.of(
                    "taskId", taskId, "tool", call.tool().name(), "toolInput", call.toolInput(), "idempotencyKey", call.fingerprint(),
                    "costUnits", cost(call.tool()), "parallel", parallel, "imageProvider", call.imageProvider())));
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_ACT, AgentEventStatus.RUNNING, "ReActAgent", Map.of(
                    "round", round, "title", "Act", "content", "Executing " + call.tool().name(), "action", call.tool().name(),
                    "tool", call.tool().name(), "toolInput", call.toolInput(), "attempt", call.attempt(), "parallel", parallel,
                    "imageProvider", call.imageProvider())));
        }
    }

    private void publishResult(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, int round, InvocationResult invocation,
                               String nextDecision, List<String> observations, boolean parallel) throws IOException {
        PreparedInvocation call = invocation.call();
        AgentToolResult result = invocation.result();
        String taskId = "react-" + round + "-" + call.tool().name().toLowerCase();
        Map<String, Object> observation = Map.ofEntries(Map.entry("round", round), Map.entry("title", "Observation"), Map.entry("content", result.summary()),
                Map.entry("tool", call.tool().name()), Map.entry("toolInput", call.toolInput()), Map.entry("toolResult", result.summary()), Map.entry("success", result.success()),
                Map.entry("invoked", result.invoked()), Map.entry("provider", safe(result.provider())), Map.entry("imageUrl", safe(result.imageUrl())), Map.entry("nextDecision", nextDecision),
                Map.entry("parallel", parallel), Map.entry("idempotencyKey", call.fingerprint()));
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_OBSERVATION,
                result.success() ? AgentEventStatus.COMPLETE : AgentEventStatus.FAILED, "ReActAgent", observation));
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT,
                result.success() ? AgentEventStatus.COMPLETE : AgentEventStatus.FAILED, "ToolRouter", Map.of("taskId", taskId, "tool", call.tool().name(), "content", result.summary(), "provider", safe(result.provider()), "parallel", parallel)));
        observations.add("Round " + round + " " + call.tool().name() + ": " + result.summary());
        publishImage(emitter, sessionId, runId, sequence, round, result);
    }

    private void publishImage(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, int round, AgentToolResult result) throws IOException {
        if (!result.success() || result.imageUrl() == null || result.imageUrl().isBlank()) return;
        String assetId = "asset-" + UUID.randomUUID();
        String title = "ReAct " + result.tool().name() + " output";
        assetStore.recordGeneratedForSession(sessionId, runId, assetId, title, result.imageUrl());
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE, "ImageToolchain", Map.of(
                "assetId", assetId, "round", round, "title", title, "imageUrl", result.imageUrl(), "content", result.summary(), "sourceTool", result.tool().name())));
    }

    private void finish(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, AgentRunRequest request, List<String> observations, boolean terminated) throws IOException {
        ModelCompletion summary = chatClient.complete("You are SummaryAgent. Write a concise Chinese delivery summary from the ReAct observations. Do not invent URLs.",
                "User task: " + request.getPrompt() + "\nObservations: " + String.join("\n", observations), request.getImageUrls());
        String fallback = observations.isEmpty() ? "No tool action was required; the ReAct loop completed." : String.join("\n", observations);
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE, "SummaryAgent", Map.of(
                "title", "SummaryAgent: ReAct delivery", "content", summary.invoked() ? summary.content() : fallback, "observations", List.copyOf(observations), "terminated", terminated)));
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE, "ReActOrchestrator", Map.of("message", "ReAct completed", "steps", observations.size())));
        historyStore.completeRun(sessionId, runId, AgentEventStatus.COMPLETE);
        emitter.complete();
    }

    private void handoffToPlanSolve(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, int round,
                                    List<String> observations, String reason) throws IOException {
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_TERMINATED, AgentEventStatus.COMPLETE, "ReActOrchestrator", Map.of(
                "round", round, "title", "ReAct handoff", "content", "ReAct requested Plan-Solve replanning.",
                "reason", safe(reason), "handoffMode", "plan-solve", "observations", List.copyOf(observations))));
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE, "ReActOrchestrator", Map.of(
                "message", "ReAct handed off to Plan-Solve", "handoffMode", "plan-solve")));
        historyStore.completeRun(sessionId, runId, AgentEventStatus.COMPLETE);
        emitter.complete();
    }
    private void terminate(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, String reason, int round, List<String> observations) throws IOException {
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_TERMINATED, AgentEventStatus.FAILED, "ReActOrchestrator", Map.of(
                "round", round, "title", "ReAct terminated", "content", reason, "reason", reason, "observations", List.copyOf(observations))));
        historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
        emitter.complete();
    }

    private void publish(SseEmitter emitter, AgentEvent event) throws IOException { historyStore.append(event); emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON)); }
    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type, AgentEventStatus status, String agent, Map<String, Object> payload) { return new AgentEvent("v5", UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(), type, status, agent, Instant.now(), payload); }
    private Duration safeTimeout(Duration value) { return value == null || value.isZero() || value.isNegative() ? Duration.ofMinutes(8) : value; }
    private String fingerprint(ReActAction action, String input, List<String> imageUrls, String provider) { return action.name() + "|" + input.trim().replaceAll("\\s+", " ").toLowerCase() + "|" + String.join("|", imageUrls) + "|" + safe(provider); }
    private int cost(AgentToolType tool) { return tool == AgentToolType.OCR ? 1 : 3; }
    private String safe(String value) { return value == null ? "" : value; }
    @PreDestroy void close() { parallelWorkers.shutdown(); }

    private record PreparedInvocation(ReActAction action, AgentToolType tool, String toolInput, String imageProvider, int attempt, String fingerprint) { }
    private record InvocationResult(PreparedInvocation call, AgentToolResult result) { }
    private final class ToolBudget {
        private final int maxCalls; private final int maxCostUnits; private int calls; private int costUnits;
        private ToolBudget(int maxCalls, int maxCostUnits) { this.maxCalls = maxCalls; this.maxCostUnits = maxCostUnits; }
        private boolean reserve(List<PreparedInvocation> invocations) { int nextCalls = calls + invocations.size(); int nextCost = costUnits + invocations.stream().mapToInt(value -> cost(value.tool())).sum(); if (nextCalls > maxCalls || nextCost > maxCostUnits) return false; calls = nextCalls; costUnits = nextCost; return true; }
    }
}