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

/** PlanningAgent adapter: model JSON first, deterministic safe plan only on outage or invalid JSON. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructuredPlanGenerator {
    private final OpenAiCompatibleChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PlanJsonSchemaValidator validator;

    public GeneratedPlan generate(AgentRunRequest request) {
        ModelCompletion completion = chatClient.complete(systemPrompt(), userPrompt(request), request.getImageUrls());
        if (completion.invoked()) {
            try {
                StructuredAgentPlan plan = objectMapper.readValue(extractJson(completion.content()), StructuredAgentPlan.class);
                PlanValidationResult validation = validator.validate(plan);
                if (validation.valid()) return new GeneratedPlan(plan, true, completion.provider(), List.of());
                log.warn("PlanningAgent JSON failed schema validation: {}", validation.errors());
                return new GeneratedPlan(fallback(request), false, completion.provider(), validation.errors());
            } catch (Exception error) {
                log.warn("PlanningAgent returned unreadable JSON", error);
                return new GeneratedPlan(fallback(request), false, completion.provider(), List.of("model output was not valid JSON"));
            }
        }
        return new GeneratedPlan(fallback(request), false, completion.provider(), List.of("model unavailable; deterministic safety plan used"));
    }

    private String systemPrompt() {
        return "You are PlanningAgent for an image AI product. Return ONLY a JSON object that matches this contract: "
                + "{version:'1.0',goal:string,tasks:[{id:string,title:string,kind:'OCR'|'IMAGE_GENERATE'|'IMAGE_EDIT'|'HUMAN_CONFIRMATION',prompt:string,dependsOn:string[],parallelGroup:string,maxAttempts:1|2|3,skipWhen:'never'|'no_input_image'|'previous_failed'|'tool_unavailable',requiresConfirmation:boolean,confirmationMessage:string}]}. "
                + "Use at most 8 tasks. IDs are lowercase kebab-case. Build a DAG using dependsOn. Independent tasks may share a parallelGroup. "
                + "For an explicit request to review or confirm a prompt before a costly image operation, insert a HUMAN_CONFIRMATION node before that operation. "
                + "Never choose IMAGE_EDIT without an input image. Use available tools only. Do not write markdown.";
    }

    private String userPrompt(AgentRunRequest request) {
        return "Task: " + request.getPrompt() + "\nInput image count: " + request.getImageUrls().size()
                + "\nAvailable tools: OCR, IMAGE_GENERATE, IMAGE_EDIT"
                + "\nUser-selected tools (hard preference, empty means model decides): " + String.join(",", request.getPreferredTools());
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

    private StructuredAgentPlan fallback(AgentRunRequest request) {
        List<AgentToolType> tools = preferredTools(request);
        if (tools.isEmpty()) {
            String prompt = request.getPrompt().toLowerCase(Locale.ROOT);
            if (!request.getImageUrls().isEmpty() && contains(prompt, "ocr", "extract text", "read text", "识别", "文字")) tools.add(AgentToolType.OCR);
            if (!request.getImageUrls().isEmpty() && contains(prompt, "edit", "background", "style", "replace", "编辑", "背景", "替换", "风格")) tools.add(AgentToolType.IMAGE_EDIT);
            else if (contains(prompt, "generate", "create", "draw", "生成", "海报", "动漫", "画一")) tools.add(AgentToolType.IMAGE_GENERATE);
        }
        if (tools.isEmpty()) tools.add(AgentToolType.IMAGE_GENERATE);
        List<PlanTaskSpec> tasks = new ArrayList<>();
        boolean asksForConfirmation = contains(request.getPrompt().toLowerCase(Locale.ROOT), "confirm", "approval", "\u786e\u8ba4", "\u5ba1\u6838");
        if (asksForConfirmation) {
            tasks.add(new PlanTaskSpec("confirm-prompt", "Confirm prompt", PlanTaskKind.HUMAN_CONFIRMATION, "", List.of(), "review", 1,
                    "never", false, "Confirm the final prompt before the image task starts."));
        }
        for (int index = 0; index < tools.size(); index++) {
            AgentToolType tool = tools.get(index);
            tasks.add(new PlanTaskSpec("task-" + (index + 1), tool.getDisplayName(), PlanTaskKind.valueOf(tool.name()), request.getPrompt(), asksForConfirmation ? List.of("confirm-prompt") : List.of(), "primary", 2,
                    tool == AgentToolType.IMAGE_EDIT || tool == AgentToolType.OCR ? "no_input_image" : "never", false, ""));
        }
        return new StructuredAgentPlan("1.0", request.getPrompt(), tasks);
    }

    private List<AgentToolType> preferredTools(AgentRunRequest request) {
        List<AgentToolType> tools = new ArrayList<>();
        for (String value : request.getPreferredTools()) {
            try { tools.add(AgentToolType.valueOf(value.trim().toUpperCase(Locale.ROOT))); }
            catch (Exception ignored) { }
        }
        return tools.stream().distinct().toList();
    }

    private boolean contains(String input, String... values) {
        for (String value : values) if (input.contains(value)) return true;
        return false;
    }

    public record GeneratedPlan(StructuredAgentPlan plan, boolean modelGenerated, String provider, List<String> validationErrors) { }
}
