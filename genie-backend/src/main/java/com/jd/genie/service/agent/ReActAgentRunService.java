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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded Think -> Act -> Observation -> Next Action runtime for React mode. */
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

    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        AgentPrincipal owner = AgentRequestUserContext.current();
        historyStore.startRun(sessionId, runId, request);
        ThreadUtil.execute(() -> execute(emitter, sessionId, runId, request, owner, new AtomicLong()));
        return emitter;
    }

    private void execute(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request, AgentPrincipal owner, AtomicLong sequence) {
        Instant startedAt = Instant.now();
        Instant deadline = startedAt.plus(safeTimeout(properties.getTimeout()));
        int maxSteps = Math.max(1, Math.min(properties.getMaxSteps(), 12));
        int repeatLimit = Math.max(1, Math.min(properties.getRepeatedToolLimit(), 5));
        List<String> observations = new ArrayList<>();
        Map<String, Integer> fingerprintCounts = new HashMap<>();
        try {
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING, "ReActOrchestrator", Map.of(
                    "mode", "react-loop", "prompt", request.getPrompt(), "imageUrls", request.getImageUrls(), "maxSteps", maxSteps,
                    "timeoutSeconds", safeTimeout(properties.getTimeout()).toSeconds(), "repeatToolLimit", repeatLimit)));
            for (int round = 1; round <= maxSteps; round++) {
                if (Instant.now().isAfter(deadline)) {
                    terminate(emitter, sessionId, runId, sequence, "timeout", round, observations);
                    return;
                }
                ReActDecisionGenerator.GeneratedDecision generated = decisionGenerator.decide(request, round, String.join("\n", observations));
                ReActDecision decision = generated.decision();
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_THINK, AgentEventStatus.COMPLETE, "ReActAgent", Map.of(
                        "round", round, "title", "Think", "content", decision.reasoning(), "reasoningSummary", decision.reasoning(),
                        "modelGenerated", generated.modelGenerated(), "modelProvider", generated.provider(), "priorObservations", List.copyOf(observations))));
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_DECISION, AgentEventStatus.COMPLETE, "ReActAgent", Map.of(
                        "round", round, "title", "Next action", "content", decision.nextDecision(), "action", decision.action().name(),
                        "toolInput", decision.toolInput(), "nextDecision", decision.nextDecision())));

                if (decision.action() == ReActAction.FINISH) {
                    finish(emitter, sessionId, runId, sequence, request, observations, false);
                    return;
                }
                String fingerprint = fingerprint(decision.action(), decision.toolInput(), request.getImageUrls());
                int count = fingerprintCounts.merge(fingerprint, 1, Integer::sum);
                if (count > repeatLimit) {
                    terminate(emitter, sessionId, runId, sequence, "repeated tool call protection", round, observations);
                    return;
                }
                AgentToolType tool = AgentToolType.valueOf(decision.action().name());
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_ACT, AgentEventStatus.RUNNING, "ReActAgent", Map.of(
                        "round", round, "title", "Act", "content", "Executing " + tool.name(), "action", tool.name(), "tool", tool.name(),
                        "toolInput", decision.toolInput(), "attempt", count)));
                AgentToolResult result = toolClient.execute(tool, decision.toolInput(), request.getImageUrls(), request.getImageProvider(), owner.userId());
                Map<String, Object> observationPayload = Map.ofEntries(Map.entry("round", round), Map.entry("title", "Observation"), Map.entry("content", result.summary()),
                        Map.entry("tool", tool.name()), Map.entry("toolInput", decision.toolInput()), Map.entry("toolResult", result.summary()), Map.entry("success", result.success()),
                        Map.entry("invoked", result.invoked()), Map.entry("provider", safe(result.provider())), Map.entry("imageUrl", safe(result.imageUrl())), Map.entry("nextDecision", decision.nextDecision()));
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_OBSERVATION,
                        result.success() ? AgentEventStatus.COMPLETE : AgentEventStatus.FAILED, "ReActAgent", observationPayload));
                observations.add("Round " + round + " " + tool.name() + ": " + result.summary());
                publishImage(emitter, sessionId, runId, sequence, round, result);
            }
            terminate(emitter, sessionId, runId, sequence, "maximum step limit reached", maxSteps, observations);
        } catch (Exception error) {
            log.error("ReAct run {} failed", runId, error);
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            try {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED, "ReActOrchestrator", Map.of(
                        "code", "REACT_RUN_FAILED", "message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())));
            } catch (IOException ignored) { }
            emitter.completeWithError(error);
        }
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

    private void terminate(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, String reason, int round, List<String> observations) throws IOException {
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.REACT_TERMINATED, AgentEventStatus.FAILED, "ReActOrchestrator", Map.of(
                "round", round, "title", "ReAct terminated", "content", reason, "reason", reason, "observations", List.copyOf(observations))));
        historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
        emitter.complete();
    }

    private void publish(SseEmitter emitter, AgentEvent event) throws IOException {
        historyStore.append(event);
        emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type, AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent("v4", UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(), type, status, agent, Instant.now(), payload);
    }

    private Duration safeTimeout(Duration value) { return value == null || value.isZero() || value.isNegative() ? Duration.ofMinutes(8) : value; }
    private String fingerprint(ReActAction action, String input, List<String> imageUrls) { return action.name() + "|" + input.trim().replaceAll("\\s+", " ").toLowerCase() + "|" + String.join("|", imageUrls); }
    private String safe(String value) { return value == null ? "" : value; }
}
