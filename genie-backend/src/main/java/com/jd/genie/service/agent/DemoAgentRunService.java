package com.jd.genie.service.agent;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Temporary execution adapter for end-to-end SSE integration. Replace event creation with the
 * PlanningAgent, ExecutorAgent and SummaryAgent adapters without changing the public protocol.
 */
@Slf4j
@Service
public class DemoAgentRunService implements AgentRunService {
    private static final String SCHEMA_VERSION = "v1";
    private static final long RUN_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "agent-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(RUN_TIMEOUT_MILLIS);
        AtomicLong sequence = new AtomicLong();
        AtomicBoolean open = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> sendHeartbeat(emitter, sessionId, runId, sequence, open),
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        emitter.onCompletion(() -> close(open, heartbeat, sessionId, runId));
        emitter.onTimeout(() -> {
            log.warn("Agent run {} timed out", runId);
            close(open, heartbeat, sessionId, runId);
            emitter.complete();
        });
        emitter.onError(error -> {
            log.info("Agent run {} SSE connection closed: {}", runId, error.getMessage());
            close(open, heartbeat, sessionId, runId);
        });

        ThreadUtil.execute(() -> executeRun(emitter, sessionId, runId, request, sequence, open, heartbeat));
        return emitter;
    }

    private void executeRun(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request,
                            AtomicLong sequence, AtomicBoolean open, ScheduledFuture<?> heartbeat) {
        try {
            send(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING,
                    "AgentOrchestrator", Map.of("mode", request.getMode(), "prompt", request.getPrompt(),
                            "imageUrls", request.getImageUrls())));
            pause(350);

            send(emitter, event(sessionId, runId, sequence, AgentEventType.PLAN, AgentEventStatus.COMPLETE,
                    "PlanningAgent", Map.of(
                            "title", "PlanningAgent：拆解交付路径",
                            "content", request.getImageUrls().isEmpty()
                                    ? "未检测到参考图，已按文本目标建立任务图。"
                                    : "已将 image_url 注入规划与执行的共享多模态上下文。",
                            "steps", List.of("识别目标与交付物", "检索提示词规范", "编排并发工具调用"))));
            pause(500);

            send(emitter, event(sessionId, runId, sequence, AgentEventType.TASK, AgentEventStatus.COMPLETE,
                    "ExecutorAgent", Map.of(
                            "taskId", "task-01",
                            "title", "ExecutorAgent：子任务 01 / 02",
                            "content", "PromptOpAgent 已生成结构化图像提示词并提交工具执行队列。")));
            pause(450);

            send(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT, AgentEventStatus.COMPLETE,
                    "ImageToolchain", Map.of(
                            "tool", "PromptOpAgent + Vision + SeedDream",
                            "title", "工具并发执行：视觉理解 + 图像生成",
                            "content", "视觉约束提取完成，生成任务已进入质量校验。")));
            pause(450);

            send(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE,
                    "ImageToolchain", Map.of(
                            "assetId", "asset-" + runId,
                            "title", "中间产物：视觉方案 A",
                            "imageUrl", "https://example.invalid/generated/" + runId + ".png",
                            "content", "图像产物已写入工作空间，等待 COS 上传适配器接管真实 URL。")));
            pause(300);

            send(emitter, event(sessionId, runId, sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE,
                    "SummaryAgent", Map.of(
                            "title", "SummaryAgent：交付已完成",
                            "content", "已汇总规划、工具调用与产物；事件可按 messageType 持久化并完整回放。")));
            send(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE,
                    "AgentOrchestrator", Map.of("message", "run completed")));
            emitter.complete();
        } catch (Exception error) {
            log.error("Agent run {} failed", runId, error);
            send(emitter, event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED,
                    "AgentOrchestrator", Map.of("code", "AGENT_RUN_FAILED", "message", error.getMessage())));
            emitter.completeWithError(error);
        } finally {
            close(open, heartbeat, sessionId, runId);
        }
    }

    private void sendHeartbeat(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, AtomicBoolean open) {
        if (open.get()) {
            send(emitter, event(sessionId, runId, sequence, AgentEventType.HEARTBEAT, AgentEventStatus.RUNNING,
                    "AgentOrchestrator", Map.of()));
        }
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType messageType,
                             AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent(SCHEMA_VERSION, UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(),
                messageType, status, agent, Instant.now(), payload);
    }

    private void send(SseEmitter emitter, AgentEvent event) {
        try {
            emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException error) {
            throw new IllegalStateException("Unable to send agent SSE event", error);
        }
    }

    private void close(AtomicBoolean open, ScheduledFuture<?> heartbeat, String sessionId, String runId) {
        if (open.compareAndSet(true, false)) {
            heartbeat.cancel(true);
            log.info("Agent SSE run {} for session {} closed", runId, sessionId);
        }
    }

    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Agent execution interrupted", error);
        }
    }
}
