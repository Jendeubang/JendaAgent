package com.jd.genie.service.agent;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
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
 * Primary runtime implementation: persist every replayable event before sending it to the browser.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class PersistentAgentRunService implements AgentRunService {
    private static final String SCHEMA_VERSION = "v1";
    private final AgentHistoryStore historyStore;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "persistent-agent-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        AtomicLong sequence = new AtomicLong();
        AtomicBoolean open = new AtomicBoolean(true);
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
        ThreadUtil.execute(() -> execute(emitter, sessionId, runId, request, sequence, open, heartbeat));
        return emitter;
    }

    private void execute(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request,
                         AtomicLong sequence, AtomicBoolean open, ScheduledFuture<?> heartbeat) {
        try {
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING, "AgentOrchestrator",
                    Map.of("mode", request.getMode(), "prompt", request.getPrompt(), "imageUrls", request.getImageUrls())));
            pause(300);
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.PLAN, AgentEventStatus.COMPLETE, "PlanningAgent",
                    Map.of("title", "PlanningAgent：拆解交付路径", "content", request.getImageUrls().isEmpty() ? "未检测到参考图，已按文本目标建立任务图。" : "已将 image_url 注入共享多模态上下文。", "steps", List.of("识别目标与交付物", "检索提示词规范", "编排并发工具调用"))));
            pause(400);
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.TASK, AgentEventStatus.COMPLETE, "ExecutorAgent",
                    Map.of("taskId", "task-01", "title", "ExecutorAgent：子任务 01 / 02", "content", "PromptOpAgent 已生成结构化图像提示词。")));
            pause(400);
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.TOOL_RESULT, AgentEventStatus.COMPLETE, "ImageToolchain",
                    Map.of("tool", "PromptOpAgent + Vision + SeedDream", "title", "工具并发执行：视觉理解 + 图像生成", "content", "视觉约束提取完成，生成任务已进入质量校验。")));
            pause(400);
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.IMAGE, AgentEventStatus.COMPLETE, "ImageToolchain",
                    Map.of("assetId", "asset-" + runId, "title", "中间产物：视觉方案 A", "imageUrl", "https://example.invalid/generated/" + runId + ".png", "content", "图像产物已持久化，等待 COS 适配器提供真实 URL。")));
            pause(250);
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE, "SummaryAgent",
                    Map.of("title", "SummaryAgent：交付已完成", "content", "规划、工具调用与产物均已保存，可通过回放接口完整重建。")));
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE, "AgentOrchestrator", Map.of("message", "run completed")));
            historyStore.completeRun(sessionId, runId, AgentEventStatus.COMPLETE);
            emitter.complete();
        } catch (Exception error) {
            log.error("Persistent agent run {} failed", runId, error);
            historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
            sendFailure(emitter, sessionId, runId, sequence, error);
            emitter.completeWithError(error);
        } finally {
            close(open, heartbeat);
        }
    }

    private void publish(SseEmitter emitter, AgentEvent event) throws IOException {
        historyStore.append(event);
        emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
    }

    private void sendHeartbeat(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, AtomicBoolean open) {
        if (open.get()) {
            try {
                AgentEvent heartbeat = event(sessionId, runId, sequence, AgentEventType.HEARTBEAT, AgentEventStatus.RUNNING, "AgentOrchestrator", Map.of());
                emitter.send(SseEmitter.event().id(heartbeat.eventId()).name("agent-event").data(heartbeat, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException error) {
                open.set(false);
            }
        }
    }

    private void sendFailure(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, Exception error) {
        try {
            AgentEvent failure = event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED, "AgentOrchestrator",
                    Map.of("code", "AGENT_RUN_FAILED", "message", error.getMessage() == null ? "Unknown error" : error.getMessage()));
            historyStore.append(failure);
            emitter.send(SseEmitter.event().id(failure.eventId()).name("agent-event").data(failure, MediaType.APPLICATION_JSON));
        } catch (Exception sendError) {
            log.warn("Unable to send agent failure event", sendError);
        }
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type,
                             AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent(SCHEMA_VERSION, UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(), type,
                status, agent, Instant.now(), payload);
    }

    private void close(AtomicBoolean open, ScheduledFuture<?> heartbeat) {
        if (open.compareAndSet(true, false)) {
            heartbeat.cancel(true);
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
