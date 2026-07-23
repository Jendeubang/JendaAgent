package com.jd.genie.service.agent;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real runtime used by the agent API. Every externally visible event is saved before SSE delivery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelToolAgentRunService {
    private static final String SCHEMA_VERSION = "v1";
    private final AgentHistoryStore historyStore;
    private final OpenAiCompatibleChatClient chatClient;
    private final HttpAgentToolClient toolClient;
    private final AgentAssetMetadataStore assetStore;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "agent-runtime-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        AtomicLong sequence = new AtomicLong();
        AtomicBoolean open = new AtomicBoolean(true);
        AgentPrincipal owner = AgentRequestUserContext.current();
        historyStore.startRun(sessionId, runId, request);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter, sessionId, runId, sequence, open), 15, 15, TimeUnit.SECONDS);

        emitter.onCompletion(() -> close(open, heartbeat));
        emitter.onTimeout(() -> {
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            close(open, heartbeat);
            emitter.complete();
        });
        emitter.onError(error -> close(open, heartbeat));
        ThreadUtil.execute(() -> execute(emitter, sessionId, runId, request, owner, sequence, open, heartbeat));
        return emitter;
    }

    private void execute(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request,
                         AgentPrincipal owner, AtomicLong sequence, AtomicBoolean open, ScheduledFuture<?> heartbeat) {
        try {
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING,
                    "AgentOrchestrator", Map.of("mode", request.getMode(), "prompt", request.getPrompt(),
                    "imageUrls", request.getImageUrls(), "runtime", "model-toolchain-v1")));

            ModelCompletion plan = chatClient.complete(
                    "You are PlanningAgent. Create a concise execution plan for the user task. "
                            + "Consider every supplied image_url as shared multimodal context. Answer in Chinese.",
                    request.getPrompt(), request.getImageUrls());
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.PLAN, AgentEventStatus.COMPLETE,
                    "PlanningAgent", Map.of(
                    "title", "PlanningAgent: 任务规划",
                    "content", plan.invoked() ? plan.content() : fallbackPlan(request),
                    "modelInvoked", plan.invoked(),
                    "modelProvider", plan.provider(),
                    "imageContextCount", request.getImageUrls().size())));

            List<AgentToolType> tools = selectTools(request);
            for (AgentToolType tool : tools) {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.TASK, AgentEventStatus.RUNNING,
                        "ExecutorAgent", Map.of(
                        "taskId", "tool-" + tool.name().toLowerCase(),
                        "title", "ExecutorAgent: " + tool.getDisplayName(),
                        "content", "已提交给 " + tool.getDisplayName() + " 工具适配器执行")));
            }
            for (AgentToolType tool : tools) {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_CALL, AgentEventStatus.RUNNING,
                        "ToolRouter", Map.of(
                        "tool", tool.name(),
                        "title", "ToolRouter: " + tool.getDisplayName() + " 调用中",
                        "content", "已通过统一工具协议发起 " + tool.getDisplayName() + " 调用。")));
            }

            List<AgentToolResult> toolResults = executeTools(tools, request, owner.userId());
            if (toolResults.isEmpty()) {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT, AgentEventStatus.COMPLETE,
                        "ToolRouter", Map.of(
                        "tool", "none",
                        "title", "工具链未匹配",
                        "content", "本次任务未匹配已启用的 OCR、图像生成或图像编辑工具。")));
            }
            for (AgentToolResult result : toolResults) {
                publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT,
                        result.success() ? AgentEventStatus.COMPLETE : AgentEventStatus.FAILED,
                        "ToolRouter", Map.of(
                        "tool", result.tool().name(),
                        "title", result.tool().getDisplayName() + (result.success() ? " 执行完成" : " 未完成"),
                        "content", result.summary(),
                        "invoked", result.invoked(),
                        "provider", result.provider())));
                if (result.success() && result.imageUrl() != null && !result.imageUrl().isBlank()) {
                    String assetId = "asset-" + UUID.randomUUID();
                    assetStore.recordGeneratedForSession(sessionId, runId, assetId, result.tool().getDisplayName() + " output", result.imageUrl());
                    publish(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE,
                            "ImageToolchain", Map.of(
                            "assetId", assetId,
                            "title", result.tool().getDisplayName() + " 产物",
                            "imageUrl", result.imageUrl(),
                            "content", result.summary(),
                            "sourceTool", result.tool().name())));
                }
            }

            String observation = summarizeToolResults(toolResults);
            ModelCompletion summary = chatClient.complete(
                    "You are SummaryAgent. Produce a concise Chinese delivery summary. Mention completed tools, "
                            + "unavailable tools, and image outputs. Do not fabricate an output URL.",
                    "用户任务: " + request.getPrompt() + "\n执行计划: " + plan.content() + "\n工具结果: " + observation,
                    request.getImageUrls());
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE,
                    "SummaryAgent", Map.of(
                    "title", "SummaryAgent: 交付汇总",
                    "content", summary.invoked() ? summary.content() : fallbackSummary(toolResults),
                    "modelInvoked", summary.invoked(),
                    "modelProvider", summary.provider())));
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE,
                    "AgentOrchestrator", Map.of("message", "run completed")));
            historyStore.completeRun(sessionId, runId, AgentEventStatus.COMPLETE);
            emitter.complete();
        } catch (Exception error) {
            log.error("Model/tool agent run {} failed", runId, error);
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            sendFailure(emitter, sessionId, runId, sequence, error);
            emitter.completeWithError(error);
        } finally {
            close(open, heartbeat);
        }
    }

    private List<AgentToolType> selectTools(AgentRunRequest request) {
        List<AgentToolType> preferred = preferredTools(request);
        if (!preferred.isEmpty()) {
            return preferred;
        }
        String prompt = request.getPrompt().toLowerCase();
        boolean hasReferenceImage = !request.getImageUrls().isEmpty();
        boolean wantsGeneration = containsAny(prompt,
                "\u751f\u6210\u4e00\u5f20", "\u751f\u6210\u56fe\u7247", "\u751f\u6210\u56fe\u50cf", "\u7ed8\u5236", "\u753b\u4e00\u5f20", "\u521b\u5efa\u6d77\u62a5", "\u6587\u751f\u56fe", "\u751f\u6210", "\u52a8\u6f2b", "\u6d77\u62a5",
                "generate an image", "create an image", "draw an image");
        boolean wantsEdit = containsAny(prompt,
                "\u7f16\u8f91", "\u80cc\u666f", "\u98ce\u683c", "\u6e05\u6670", "\u4fee\u590d", "\u66ff\u6362", "\u5c40\u90e8", "edit", "background", "style");
        List<AgentToolType> selected = new ArrayList<>();
        if (hasReferenceImage && containsAny(prompt, "\u8bc6\u522b", "\u63d0\u53d6\u6587\u5b57", "\u56fe\u4e2d\u6587\u5b57", "ocr", "extract text", "read text")) {
            selected.add(AgentToolType.OCR);
        }
        if (wantsGeneration) {
            selected.add(AgentToolType.IMAGE_GENERATE);
        } else if (hasReferenceImage && wantsEdit) {
            selected.add(AgentToolType.IMAGE_EDIT);
        }
        return selected;
    }

    private List<AgentToolType> preferredTools(AgentRunRequest request) {
        List<AgentToolType> selected = new ArrayList<>();
        if (request.getPreferredTools() == null) {
            return selected;
        }
        for (String value : request.getPreferredTools()) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                selected.add(AgentToolType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                log.warn("Ignoring unsupported preferred tool: {}", value);
            }
        }
        return selected.stream().distinct().toList();
    }
    private List<AgentToolResult> executeTools(List<AgentToolType> tools, AgentRunRequest request, String ownerUserId) {
        List<CompletableFuture<AgentToolResult>> futures = tools.stream()
                .map(tool -> CompletableFuture.supplyAsync(() -> toolClient.execute(
                        tool, request.getPrompt(), request.getImageUrls(), request.getImageProvider(), ownerUserId)))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private void publish(SseEmitter emitter, AgentEvent event) throws IOException {
        historyStore.append(event);
        emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
    }

    private void sendHeartbeat(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, AtomicBoolean open) {
        if (!open.get()) {
            return;
        }
        try {
            AgentEvent heartbeat = event(sessionId, runId, sequence, AgentEventType.HEARTBEAT, AgentEventStatus.RUNNING,
                    "AgentOrchestrator", Map.of());
            emitter.send(SseEmitter.event().id(heartbeat.eventId()).name("agent-event").data(heartbeat, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException error) {
            open.set(false);
        }
    }

    private void sendFailure(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, Exception error) {
        try {
            AgentEvent failure = event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED,
                    "AgentOrchestrator", Map.of(
                    "code", "AGENT_RUN_FAILED",
                    "message", error.getMessage() == null ? "Unknown error" : error.getMessage()));
            historyStore.append(failure);
            emitter.send(SseEmitter.event().id(failure.eventId()).name("agent-event").data(failure, MediaType.APPLICATION_JSON));
        } catch (Exception sendError) {
            log.warn("Unable to send agent failure event", sendError);
        }
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type,
                             AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent(SCHEMA_VERSION, UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(),
                type, status, agent, Instant.now(), payload);
    }

    private String fallbackPlan(AgentRunRequest request) {
        return "模型未启用，已按规则选择工具链。任务包含 " + request.getImageUrls().size()
                + " 张参考图；配置模型后 PlanningAgent 将生成真实计划。";
    }

    private String fallbackSummary(List<AgentToolResult> results) {
        return "模型未启用，以下为工具执行记录：" + summarizeToolResults(results);
    }

    private String summarizeToolResults(List<AgentToolResult> results) {
        if (results.isEmpty()) {
            return "无工具调用";
        }
        return results.stream().map(result -> result.tool().name() + ": " + result.summary()).reduce((left, right) -> left + "；" + right).orElse("");
    }

    private boolean containsAny(String input, String... words) {
        for (String word : words) {
            if (input.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private void close(AtomicBoolean open, ScheduledFuture<?> heartbeat) {
        if (open.compareAndSet(true, false)) {
            heartbeat.cancel(true);
        }
    }
}
