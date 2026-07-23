$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$path = Join-Path $root "genie-backend\src\main\java\com\jd\genie\service\agent\DynamicPlanSolveAgentRunService.java"

$source = @'
package com.jd.genie.service.agent;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded Plan-Solve runtime. Tool failures become shared memory for the next planning round.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicPlanSolveAgentRunService {
    private static final int MAX_ROUNDS = 3;

    private final AgentHistoryStore historyStore;
    private final OpenAiCompatibleChatClient chatClient;
    private final HttpAgentToolClient toolClient;

    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        AtomicLong sequence = new AtomicLong();
        historyStore.startRun(sessionId, runId, request);
        ThreadUtil.execute(() -> execute(emitter, sessionId, runId, request, sequence));
        return emitter;
    }

    private void execute(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request, AtomicLong sequence) {
        List<String> memory = new ArrayList<>();
        List<AgentToolResult> allResults = new ArrayList<>();
        List<AgentToolType> tools = selectInitialTools(request);
        try {
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING,
                    "PlanSolveOrchestrator", Map.of(
                    "mode", "plan-solve-dynamic",
                    "prompt", request.getPrompt(),
                    "imageUrls", request.getImageUrls(),
                    "maxRounds", MAX_ROUNDS)));

            for (int round = 1; round <= MAX_ROUNDS; round++) {
                boolean replan = round > 1;
                ModelCompletion plan = chatClient.complete(
                        "You are PlanningAgent in a bounded Plan-Solve loop. Produce a concise execution plan in Chinese. "
                                + "Use shared memory and do not repeat unavailable actions. Return plain text.",
                        "User task: " + request.getPrompt()
                                + "\nRound: " + round
                                + "\nShared memory: " + (memory.isEmpty() ? "none" : String.join("; ", memory))
                                + "\nCandidate tools: " + toolNames(tools),
                        request.getImageUrls());
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.PLAN, AgentEventStatus.COMPLETE,
                        "PlanningAgent", Map.of(
                        "title", "PlanningAgent: Round " + round + (replan ? " replan" : " plan"),
                        "content", plan.invoked() ? plan.content() : fallbackPlan(round, tools, memory),
                        "round", round,
                        "replan", replan,
                        "steps", steps(round, tools),
                        "sharedMemory", List.copyOf(memory))));

                if (tools.isEmpty()) {
                    memory.add("Round " + round + " has no applicable tool; continue to delivery summary.");
                    break;
                }

                for (AgentToolType tool : tools) {
                    publish(emitter, event(sessionId, runId, sequence, AgentEventType.TASK, AgentEventStatus.RUNNING,
                            "ExecutorAgent", Map.of(
                            "taskId", "round-" + round + "-" + tool.name().toLowerCase(),
                            "round", round,
                            "title", "ExecutorAgent: Round " + round + " " + tool.getDisplayName(),
                            "content", "Scheduled through the concurrent tool executor.")));
                    publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING,
                            "ToolRouter", Map.of(
                            "tool", tool.name(),
                            "round", round,
                            "title", "ToolRouter: " + tool.getDisplayName() + " calling",
                            "content", "Started through the normalized tool protocol.")));
                }

                List<AgentToolResult> roundResults = executeTools(tools, request);
                allResults.addAll(roundResults);
                for (AgentToolResult result : roundResults) {
                    AgentEventStatus status = result.success() ? AgentEventStatus.COMPLETE : AgentEventStatus.FAILED;
                    publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT, status,
                            "ToolRouter", Map.of(
                            "tool", result.tool().name(),
                            "round", round,
                            "invoked", result.invoked(),
                            "provider", result.provider(),
                            "title", result.tool().getDisplayName() + (result.success() ? " completed" : " failed"),
                            "content", result.summary())));
                    if (result.success() && result.imageUrl() != null && !result.imageUrl().isBlank()) {
                        publish(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE,
                                "ImageToolchain", Map.of(
                                "assetId", "asset-" + UUID.randomUUID(),
                                "round", round,
                                "title", result.tool().getDisplayName() + " output",
                                "imageUrl", result.imageUrl(),
                                "content", result.summary(),
                                "sourceTool", result.tool().name())));
                    }
                }

                List<AgentToolResult> failed = roundResults.stream().filter(result -> !result.success()).toList();
                if (failed.isEmpty()) {
                    memory.add("Round " + round + " completed: " + summarize(roundResults));
                    break;
                }
                memory.add("Round " + round + " observed: " + summarize(roundResults));
                tools = failed.stream()
                        .filter(AgentToolResult::invoked)
                        .map(AgentToolResult::tool)
                        .distinct()
                        .toList();
                if (tools.isEmpty()) {
                    memory.add("Unavailable tools are not retried; the next round revises the delivery plan.");
                    if (round < MAX_ROUNDS) {
                        continue;
                    }
                    break;
                }
                if (round == MAX_ROUNDS) {
                    memory.add("Reached the maximum planning round limit.");
                }
            }

            ModelCompletion summary = chatClient.complete(
                    "You are SummaryAgent. Produce a concise Chinese delivery summary. State what completed, what failed, "
                            + "and whether a re-plan occurred. Do not invent output URLs.",
                    "User task: " + request.getPrompt()
                            + "\nShared memory: " + String.join("; ", memory)
                            + "\nAll tool results: " + summarize(allResults),
                    request.getImageUrls());
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE,
                    "SummaryAgent", Map.of(
                    "title", "SummaryAgent: Dynamic delivery",
                    "content", summary.invoked() ? summary.content() : fallbackSummary(memory, allResults),
                    "rounds", memory.size(),
                    "sharedMemory", List.copyOf(memory))));
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE,
                    "PlanSolveOrchestrator", Map.of(
                    "message", "dynamic plan-solve completed",
                    "sharedMemory", List.copyOf(memory))));
            historyStore.completeRun(sessionId, runId, AgentEventStatus.COMPLETE);
            emitter.complete();
        } catch (Exception error) {
            log.error("Dynamic plan-solve run {} failed", runId, error);
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            try {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED,
                        "PlanSolveOrchestrator", Map.of(
                        "code", "DYNAMIC_PLAN_SOLVE_FAILED",
                        "message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())));
            } catch (IOException ignored) {
                // The browser may already have disconnected.
            }
            emitter.completeWithError(error);
        }
    }

    private List<AgentToolType> selectInitialTools(AgentRunRequest request) {
        String prompt = request.getPrompt().toLowerCase();
        List<AgentToolType> selected = new ArrayList<>();
        boolean wantsGeneration = contains(prompt,
                "\u751f\u6210\u4e00\u5f20", "\u751f\u6210\u56fe\u7247", "\u751f\u6210\u56fe\u50cf", "\u7ed8\u5236", "\u753b\u4e00\u5f20", "\u521b\u5efa\u6d77\u62a5", "\u6587\u751f\u56fe",
                "generate an image", "create an image", "draw an image");
        boolean wantsOcr = !request.getImageUrls().isEmpty() && contains(prompt,
                "\u8bc6\u522b", "\u63d0\u53d6\u6587\u5b57", "\u56fe\u4e2d\u6587\u5b57", "ocr", "extract text", "read text");
        boolean wantsEdit = !request.getImageUrls().isEmpty() && contains(prompt,
                "\u7f16\u8f91", "\u80cc\u666f", "\u6e05\u6670", "\u4fee\u590d", "\u66ff\u6362", "\u5c40\u90e8", "edit", "background", "style");
        if (wantsGeneration) {
            selected.add(AgentToolType.IMAGE_GENERATE);
        } else if (wantsEdit) {
            selected.add(AgentToolType.IMAGE_EDIT);
        }
        if (wantsOcr) {
            selected.add(AgentToolType.OCR);
        }
        return selected;
    }

    private List<AgentToolResult> executeTools(List<AgentToolType> tools, AgentRunRequest request) {
        List<CompletableFuture<AgentToolResult>> futures = tools.stream()
                .map(tool -> CompletableFuture.supplyAsync(
                        () -> toolClient.execute(tool, request.getPrompt(), request.getImageUrls())))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private List<Map<String, Object>> steps(int round, List<AgentToolType> tools) {
        if (tools.isEmpty()) {
            return List.of(Map.of(
                    "taskId", "deliver-" + round,
                    "status", "ready",
                    "goal", "Summarize currently available output."));
        }
        return tools.stream().map(tool -> Map.<String, Object>of(
                "taskId", "round-" + round + "-" + tool.name().toLowerCase(),
                "tool", tool.name(),
                "status", "ready",
                "goal", "Execute " + tool.getDisplayName())).toList();
    }

    private void publish(SseEmitter emitter, AgentEvent event) throws IOException {
        historyStore.append(event);
        emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type,
                             AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent("v2", UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(),
                type, status, agent, Instant.now(), payload);
    }

    private boolean contains(String input, String... words) {
        for (String word : words) {
            if (input.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String toolNames(List<AgentToolType> tools) {
        return tools.isEmpty()
                ? "none"
                : tools.stream().map(AgentToolType::getDisplayName).reduce((left, right) -> left + ", " + right).orElse("none");
    }

    private String summarize(List<AgentToolResult> results) {
        return results.isEmpty()
                ? "no tool calls"
                : results.stream().map(result -> result.tool().name() + ": " + result.summary())
                .reduce((left, right) -> left + "; " + right).orElse("no tool calls");
    }

    private String fallbackPlan(int round, List<AgentToolType> tools, List<String> memory) {
        return "Round " + round + " will execute " + toolNames(tools)
                + ". Shared memory: " + (memory.isEmpty() ? "none" : String.join("; ", memory));
    }

    private String fallbackSummary(List<String> memory, List<AgentToolResult> results) {
        return "Dynamic plan-solve completed. Shared memory: " + String.join("; ", memory)
                + ". Tool results: " + summarize(results);
    }
}
'@

[System.IO.File]::WriteAllText($path, $source, [System.Text.UTF8Encoding]::new($false))
Write-Host "Restored $path using an ASCII-safe UTF-8 source file."
Write-Host "Step 23 dynamic Plan-Solve encoding recovery completed. Rebuild the backend."
