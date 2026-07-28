package com.jd.genie.service.agent.plansolve;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.service.agent.AgentToolType;
import com.jd.genie.service.agent.ModelCompletion;
import com.jd.genie.service.agent.OpenAiCompatibleChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** PlanningAgent adapter: model JSON first, deterministic capability-aware plan only on outage or invalid JSON. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredPlanGenerator {
    private final OpenAiCompatibleChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PlanJsonSchemaValidator validator;
    private final AgentToolCapabilityRegistry capabilities;

    public GeneratedPlan generate(AgentRunRequest request) {
        return generate(request, null, Map.of(), 1, "initial plan");
    }

    public GeneratedPlan replan(AgentRunRequest request, StructuredAgentPlan previousPlan,
                                Map<String, PlanSolveExecutionStore.TaskSnapshot> previousStates, int revisionNo) {
        return generate(request, previousPlan, previousStates, revisionNo, "previous DAG reached a terminal failure or unavailable-tool skip");
    }

    private GeneratedPlan generate(AgentRunRequest request, StructuredAgentPlan previousPlan,
                                   Map<String, PlanSolveExecutionStore.TaskSnapshot> previousStates,
                                   int revisionNo, String reason) {
        ModelCompletion completion = chatClient.complete(systemPrompt(previousPlan != null),
                userPrompt(request, previousPlan, previousStates, revisionNo, reason), request.getImageUrls());
        if (completion.invoked()) {
            try {
                StructuredAgentPlan plan = objectMapper.readValue(extractJson(completion.content()), StructuredAgentPlan.class);
                PlanValidationResult validation = validate(plan);
                if (validation.valid()) return new GeneratedPlan(plan, true, completion.provider(), List.of());
                log.warn("PlanningAgent JSON failed validation: {}", validation.errors());
                return new GeneratedPlan(fallback(request, excludedFailedTools(previousPlan, previousStates)), false, completion.provider(), validation.errors());
            } catch (Exception error) {
                log.warn("PlanningAgent returned unreadable JSON", error);
                return new GeneratedPlan(fallback(request, excludedFailedTools(previousPlan, previousStates)), false, completion.provider(), List.of("model output was not valid JSON"));
            }
        }
        return new GeneratedPlan(fallback(request, excludedFailedTools(previousPlan, previousStates)), false, completion.provider(), List.of("model unavailable; deterministic safety plan used"));
    }

    private PlanValidationResult validate(StructuredAgentPlan plan) {
        PlanValidationResult schema = validator.validate(plan);
        if (!schema.valid()) return schema;
        List<String> errors = new ArrayList<>();
        for (PlanTaskSpec task : plan.tasks()) {
            if (task.kind() == PlanTaskKind.HUMAN_CONFIRMATION) continue;
            AgentToolType type = AgentToolType.valueOf(task.kind().name());
            if (!capabilities.isAvailable(type)) errors.add("tool is unavailable: " + type.name());
            if ((type == AgentToolType.OCR || type == AgentToolType.IMAGE_EDIT) && !task.skipWhen().equals("no_input_image")) {
                errors.add("image-input tool must guard missing input: " + task.id());
            }
        }
        return errors.isEmpty() ? PlanValidationResult.success() : new PlanValidationResult(false, errors);
    }

    private String systemPrompt(boolean replanning) {
        String phase = replanning
                ? "This is a REPLAN. Produce only replacement work that can still advance the goal. Do not repeat failed tools when an alternative is available. "
                : "";
        return "You are PlanningAgent for an image AI product. " + phase + "Return ONLY a JSON object that matches this contract: "
                + "{version:'1.0',goal:string,tasks:[{id:string,title:string,kind:'OCR'|'IMAGE_GENERATE'|'IMAGE_EDIT'|'HUMAN_CONFIRMATION',prompt:string,dependsOn:string[],parallelGroup:string,maxAttempts:1|2|3,skipWhen:'never'|'no_input_image'|'previous_failed'|'tool_unavailable',requiresConfirmation:boolean,confirmationMessage:string}]}. "
                + "Use at most 8 tasks. IDs are lowercase kebab-case. Build a DAG using dependsOn. Independent tasks may share a parallelGroup. "
                + "For an explicit request to review or confirm a prompt before a costly image operation, insert a HUMAN_CONFIRMATION node before that operation. "
                + "Use available tools only. IMAGE_EDIT and OCR must use skipWhen=no_input_image. Do not write markdown.";
    }

    private String userPrompt(AgentRunRequest request, StructuredAgentPlan previousPlan,
                              Map<String, PlanSolveExecutionStore.TaskSnapshot> previousStates, int revisionNo, String reason) {
        StringBuilder value = new StringBuilder("Task: ").append(request.getPrompt())
                .append("\nInput image count: ").append(request.getImageUrls().size())
                .append("\nAvailable tools: ").append(capabilities.plannerDescription())
                .append("\nUser-selected tools (hard preference, empty means model decides): ").append(String.join(",", request.getPreferredTools()));
        if (previousPlan != null) {
            value.append("\nReplan revision: ").append(revisionNo).append("\nReason: ").append(reason)
                    .append("\nPrevious plan goal: ").append(previousPlan.goal())
                    .append("\nPrevious terminal states: ").append(stateSummary(previousStates));
        }
        return value.toString();
    }

    private String stateSummary(Map<String, PlanSolveExecutionStore.TaskSnapshot> states) {
        return states.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue().state()
                + (entry.getValue().result().isEmpty() ? "" : ":" + entry.getValue().result())).collect(java.util.stream.Collectors.joining("; "));
    }

    private String extractJson(String value) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.startsWith("```")) {
            int lineEnd = candidate.indexOf('\n');
            int close = candidate.lastIndexOf("```");
            candidate = lineEnd >= 0 && close > lineEnd ? candidate.substring(lineEnd + 1, close).trim() : candidate;
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        return start >= 0 && end > start ? candidate.substring(start, end + 1) : candidate;
    }

    private StructuredAgentPlan fallback(AgentRunRequest request, Set<AgentToolType> excluded) {
        List<AgentToolType> tools = new ArrayList<>(preferredTools(request).stream().filter(type -> !excluded.contains(type)).toList());
        if (tools.isEmpty()) {
            String prompt = request.getPrompt().toLowerCase(Locale.ROOT);
            if (!request.getImageUrls().isEmpty() && contains(prompt, "ocr", "extract text", "read text", "\u8bc6\u522b", "\u6587\u5b57")) addIfAvailable(tools, AgentToolType.OCR, excluded);
            if (!request.getImageUrls().isEmpty() && contains(prompt, "edit", "background", "style", "replace", "\u7f16\u8f91", "\u80cc\u666f", "\u66ff\u6362", "\u98ce\u683c")) addIfAvailable(tools, AgentToolType.IMAGE_EDIT, excluded);
            else if (contains(prompt, "generate", "create", "draw", "\u751f\u6210", "\u6d77\u62a5", "\u52a8\u6f2b", "\u753b\u4e00")) addIfAvailable(tools, AgentToolType.IMAGE_GENERATE, excluded);
        }
        if (tools.isEmpty()) {
            return new StructuredAgentPlan("1.0", request.getPrompt(), List.of(new PlanTaskSpec("await-tool", "Await tool configuration", PlanTaskKind.HUMAN_CONFIRMATION,
                    "", List.of(), "review", 1, "never", false, "No compatible tool is configured. Configure an available provider, then start a new run.")));
        }
        List<PlanTaskSpec> tasks = new ArrayList<>();
        boolean asksForConfirmation = contains(request.getPrompt().toLowerCase(Locale.ROOT), "confirm", "approval", "\u786e\u8ba4", "\u5ba1\u6838");
        if (asksForConfirmation) tasks.add(new PlanTaskSpec("confirm-prompt", "Confirm prompt", PlanTaskKind.HUMAN_CONFIRMATION, "", List.of(), "review", 1,
                "never", false, "Confirm the final prompt before the image task starts."));
        for (int index = 0; index < tools.size(); index++) {
            AgentToolType tool = tools.get(index);
            String guard = tool == AgentToolType.IMAGE_EDIT || tool == AgentToolType.OCR ? "no_input_image" : "never";
            tasks.add(new PlanTaskSpec("task-" + (index + 1), tool.getDisplayName(), PlanTaskKind.valueOf(tool.name()), request.getPrompt(),
                    asksForConfirmation ? List.of("confirm-prompt") : List.of(), "primary", 2, guard, false, ""));
        }
        return new StructuredAgentPlan("1.0", request.getPrompt(), tasks);
    }

    private Set<AgentToolType> excludedFailedTools(StructuredAgentPlan previousPlan, Map<String, PlanSolveExecutionStore.TaskSnapshot> states) {
        if (previousPlan == null) return Set.of();
        return previousPlan.tasks().stream().filter(task -> task.kind() != PlanTaskKind.HUMAN_CONFIRMATION)
                .filter(task -> { PlanSolveExecutionStore.TaskSnapshot state = states.get(task.id()); return state != null && state.state() == PlanTaskState.FAILED; })
                .map(task -> AgentToolType.valueOf(task.kind().name())).collect(java.util.stream.Collectors.toSet());
    }

    private void addIfAvailable(List<AgentToolType> tools, AgentToolType type, Set<AgentToolType> excluded) {
        if (!excluded.contains(type) && capabilities.isAvailable(type)) tools.add(type);
    }

    private List<AgentToolType> preferredTools(AgentRunRequest request) {
        List<AgentToolType> tools = new ArrayList<>();
        for (String value : request.getPreferredTools()) {
            try {
                AgentToolType type = AgentToolType.valueOf(value.trim().toUpperCase(Locale.ROOT));
                if (capabilities.isAvailable(type)) tools.add(type);
            } catch (Exception ignored) { }
        }
        return tools.stream().distinct().toList();
    }

    private boolean contains(String input, String... values) {
        for (String value : values) if (input.contains(value)) return true;
        return false;
    }

    public record GeneratedPlan(StructuredAgentPlan plan, boolean modelGenerated, String provider, List<String> validationErrors) { }
}