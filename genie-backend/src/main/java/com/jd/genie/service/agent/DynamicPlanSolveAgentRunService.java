package com.jd.genie.service.agent;

import com.jd.genie.agent.util.ThreadUtil;
import com.jd.genie.model.agent.AgentEvent;
import com.jd.genie.model.agent.AgentEventStatus;
import com.jd.genie.model.agent.AgentEventType;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.model.auth.AgentPrincipal;
import com.jd.genie.service.agent.plansolve.ObservableDagTaskExecutor;
import com.jd.genie.service.agent.plansolve.PlanSolveExecutionStore;
import com.jd.genie.service.agent.plansolve.PlanTaskState;
import com.jd.genie.service.agent.plansolve.PlanTaskKind;
import com.jd.genie.service.agent.plansolve.PlanTaskSpec;
import com.jd.genie.service.agent.plansolve.StructuredPlanGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Structured Plan-Solve runtime. PlanningAgent emits a validated JSON DAG;
 * execution is durable, observable, and can pause for an authenticated human.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicPlanSolveAgentRunService {
    private final AgentHistoryStore historyStore;
    private final StructuredPlanGenerator planGenerator;
    private final PlanSolveExecutionStore executionStore;
    private final ObservableDagTaskExecutor dagExecutor;
    private final OpenAiCompatibleChatClient chatClient;

    public SseEmitter startRun(String sessionId, AgentRunRequest request) {
        String runId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        AtomicLong sequence = new AtomicLong();
        AgentPrincipal owner = AgentRequestUserContext.current();
        historyStore.startRun(sessionId, runId, request);
        ThreadUtil.execute(() -> startExecution(emitter, sessionId, runId, request, owner, sequence));
        return emitter;
    }

    public SseEmitter resolveApproval(String sessionId, String runId, String approvalId, boolean approved, String note) {
        AgentPrincipal owner = AgentRequestUserContext.current();
        PlanSolveExecutionStore.StoredExecution execution = executionStore.load(owner.userId(), sessionId, runId);
        PlanSolveExecutionStore.Approval approval = executionStore.resolveApproval(owner.userId(), runId, approvalId, approved, note == null ? "" : note);
        PlanTaskSpec approvalTask = execution.plan().tasks().stream().filter(task -> task.id().equals(approval.taskId())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Approval task is absent from the stored DAG"));
        PlanTaskState nextState = approved && approvalTask.kind() != PlanTaskKind.HUMAN_CONFIRMATION ? PlanTaskState.PENDING
                : approved ? PlanTaskState.COMPLETE : PlanTaskState.SKIPPED;
        executionStore.saveTask(runId, approval.taskId(), nextState, 0,
                Map.of("approvalId", approvalId, "approved", approved, "note", note == null ? "" : note));
        executionStore.updateRunStatus(runId, "RUNNING");
        historyStore.updateRunStatus(sessionId, runId, AgentEventStatus.RUNNING);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        AtomicLong sequence = new AtomicLong(historyStore.lastSequence(runId));
        ThreadUtil.execute(() -> resumeExecution(emitter, execution, owner, approval, approved, note, sequence));
        return emitter;
    }

    private void startExecution(SseEmitter emitter, String sessionId, String runId, AgentRunRequest request, AgentPrincipal owner, AtomicLong sequence) {
        try {
            StructuredPlanGenerator.GeneratedPlan generated = planGenerator.generate(request);
            executionStore.create(sessionId, runId, owner.userId(), request, generated.plan());
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.RUN_STARTED, AgentEventStatus.RUNNING, "PlanSolveOrchestrator", Map.of(
                    "mode", "plan-solve-structured-dag", "prompt", request.getPrompt(), "imageUrls", request.getImageUrls(), "runtime", "structured-plan-v1")));
            publishPlan(emitter, sessionId, runId, sequence, generated);
            executeDag(emitter, new PlanSolveExecutionStore.StoredExecution(sessionId, runId, owner.userId(), request, generated.plan(), "RUNNING"), sequence);
        } catch (Exception error) {
            fail(emitter, sessionId, runId, sequence, error);
        }
    }

    private void resumeExecution(SseEmitter emitter, PlanSolveExecutionStore.StoredExecution execution, AgentPrincipal owner,
                                 PlanSolveExecutionStore.Approval approval, boolean approved, String note, AtomicLong sequence) {
        try {
            publish(emitter, event(execution.sessionId(), execution.runId(), sequence, AgentEventType.TASK,
                    approved ? AgentEventStatus.COMPLETE : AgentEventStatus.SKIPPED, "HumanConfirmation", Map.of(
                            "taskId", approval.taskId(), "approvalId", approval.approvalId(), "title", "Human confirmation", "content", approved ? "Approved; DAG execution resumed." : "Rejected; dependent tasks will be skipped.", "note", note == null ? "" : note)));
            executeDag(emitter, execution, sequence);
        } catch (Exception error) {
            fail(emitter, execution.sessionId(), execution.runId(), sequence, error);
        }
    }

    private void publishPlan(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, StructuredPlanGenerator.GeneratedPlan generated) throws IOException {
        publish(emitter, event(sessionId, runId, sequence, AgentEventType.PLAN, AgentEventStatus.COMPLETE, "PlanningAgent", Map.of(
                "title", "PlanningAgent: structured DAG plan", "content", generated.plan().goal(), "schemaVersion", generated.plan().version(),
                "modelGenerated", generated.modelGenerated(), "modelProvider", generated.provider(), "validationErrors", generated.validationErrors(),
                "tasks", generated.plan().tasks(), "steps", generated.plan().tasks())));
    }

    private void executeDag(SseEmitter emitter, PlanSolveExecutionStore.StoredExecution execution, AtomicLong sequence) throws Exception {
        ObservableDagTaskExecutor.ExecutionContext context = new ObservableDagTaskExecutor.ExecutionContext(
                execution.sessionId(), execution.runId(), execution.ownerUserId(), execution.request(), execution.plan());
        ObservableDagTaskExecutor.ExecutionOutcome outcome = dagExecutor.execute(context,
                (type, status, agent, payload) -> publish(emitter, event(execution.sessionId(), execution.runId(), sequence, type, status, agent, payload)));
        if (outcome.awaitingConfirmation()) {
            historyStore.updateRunStatus(execution.sessionId(), execution.runId(), AgentEventStatus.WAITING_CONFIRMATION);
            emitter.complete();
            return;
        }
        executionStore.updateRunStatus(execution.runId(), "COMPLETE");
        String observation = stateSummary(outcome.states());
        ModelCompletion summary = chatClient.complete(
                "You are SummaryAgent. Write a concise Chinese delivery summary based only on the execution states. Do not invent URLs.",
                "User task: " + execution.request().getPrompt() + "\nDAG states: " + observation,
                execution.request().getImageUrls());
        publish(emitter, event(execution.sessionId(), execution.runId(), sequence, AgentEventType.SUMMARY, AgentEventStatus.COMPLETE,
                "SummaryAgent", Map.of("title", "SummaryAgent: DAG delivery", "content", summary.invoked() ? summary.content() : observation, "dagStates", outcome.states())));
        publish(emitter, event(execution.sessionId(), execution.runId(), sequence, AgentEventType.RUN_COMPLETED, AgentEventStatus.COMPLETE,
                "PlanSolveOrchestrator", Map.of("message", "structured Plan-Solve completed", "dagStates", outcome.states())));
        historyStore.completeRun(execution.sessionId(), execution.runId(), AgentEventStatus.COMPLETE);
        emitter.complete();
    }

    private String stateSummary(Map<String, PlanSolveExecutionStore.TaskSnapshot> states) {
        long complete = states.values().stream().filter(value -> value.state() == PlanTaskState.COMPLETE).count();
        long failed = states.values().stream().filter(value -> value.state() == PlanTaskState.FAILED).count();
        long skipped = states.values().stream().filter(value -> value.state() == PlanTaskState.SKIPPED).count();
        return "DAG completed. complete=" + complete + ", failed=" + failed + ", skipped=" + skipped + ".";
    }

    private void fail(SseEmitter emitter, String sessionId, String runId, AtomicLong sequence, Exception error) {
        log.error("Structured Plan-Solve run {} failed", runId, error);
        historyStore.completeRun(sessionId, runId, AgentEventStatus.FAILED);
        try {
            publish(emitter, event(sessionId, runId, sequence, AgentEventType.ERROR, AgentEventStatus.FAILED,
                    "PlanSolveOrchestrator", Map.of("code", "STRUCTURED_PLAN_SOLVE_FAILED", "message", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage())));
        } catch (IOException ignored) {
            // Browser may have disconnected before the terminal event was persisted.
        }
        emitter.completeWithError(error);
    }

    private void publish(SseEmitter emitter, AgentEvent event) throws IOException {
        historyStore.append(event);
        emitter.send(SseEmitter.event().id(event.eventId()).name("agent-event").data(event, MediaType.APPLICATION_JSON));
    }

    private AgentEvent event(String sessionId, String runId, AtomicLong sequence, AgentEventType type,
                             AgentEventStatus status, String agent, Map<String, Object> payload) {
        return new AgentEvent("v3", UUID.randomUUID().toString(), sessionId, runId, sequence.incrementAndGet(), type, status, agent, Instant.now(), payload);
    }
}