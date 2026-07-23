package com.jd.genie.service.agent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.service.agent.AgentToolType;
import com.jd.genie.service.agent.ModelCompletion;
import com.jd.genie.service.agent.OpenAiCompatibleChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Produces the next bounded ReAct decision; a fallback keeps outages safe. */
@Component
@RequiredArgsConstructor
public class ReActDecisionGenerator {
    private final OpenAiCompatibleChatClient chatClient;
    private final ObjectMapper objectMapper;

    public GeneratedDecision decide(AgentRunRequest request, int round, String observations) {
        ModelCompletion completion = chatClient.complete(systemPrompt(), userPrompt(request, round, observations), request.getImageUrls());
        if (completion.invoked()) {
            try {
                ReActDecision decision = objectMapper.readValue(extractJson(completion.content()), ReActDecision.class);
                if (decision.action() != null) return new GeneratedDecision(normalize(decision, request), true, completion.provider());
            } catch (Exception ignored) {
                // Continue with a deterministic decision below.
            }
        }
        return new GeneratedDecision(fallback(request, observations), false, completion.provider());
    }

    private String systemPrompt() {
        return "You are a bounded ReAct agent for an image AI product. Return only JSON: "
                + "{reasoning:string,action:'OCR'|'IMAGE_GENERATE'|'IMAGE_EDIT'|'FINISH',toolInput:string,nextDecision:string}. "
                + "Reasoning is a brief operational summary, not hidden chain-of-thought. Choose one action only. "
                + "Do not choose IMAGE_EDIT or OCR without an input image. Choose FINISH when an observation satisfies the task.";
    }

    private String userPrompt(AgentRunRequest request, int round, String observations) {
        return "User task: " + request.getPrompt() + "\nRound: " + round + "\nImage count: " + request.getImageUrls().size()
                + "\nPrior observations: " + (observations.isBlank() ? "none" : observations)
                + "\nAllowed actions: OCR, IMAGE_GENERATE, IMAGE_EDIT, FINISH";
    }

    private ReActDecision normalize(ReActDecision decision, AgentRunRequest request) {
        String input = decision.toolInput().isBlank() ? request.getPrompt() : decision.toolInput();
        if ((decision.action() == ReActAction.OCR || decision.action() == ReActAction.IMAGE_EDIT) && request.getImageUrls().isEmpty()) {
            return new ReActDecision("Input image is required for the requested action.", ReActAction.FINISH, "", "Stop because no compatible input image is available.");
        }
        return new ReActDecision(limit(decision.reasoning(), 800), decision.action(), limit(input, 8000), limit(decision.nextDecision(), 800));
    }

    private ReActDecision fallback(AgentRunRequest request, String observations) {
        if (!observations.isBlank()) return new ReActDecision("A tool observation is available and the next step is delivery.", ReActAction.FINISH, "", "Generate the final delivery summary.");
        String prompt = request.getPrompt().toLowerCase(Locale.ROOT);
        ReActAction action;
        if (!request.getImageUrls().isEmpty() && contains(prompt, "ocr", "extract text", "read text", "\u8bc6\u522b", "\u6587\u5b57")) action = ReActAction.OCR;
        else if (!request.getImageUrls().isEmpty() && contains(prompt, "edit", "background", "replace", "style", "\u7f16\u8f91", "\u80cc\u666f", "\u66ff\u6362")) action = ReActAction.IMAGE_EDIT;
        else action = ReActAction.IMAGE_GENERATE;
        return new ReActDecision("Model decision is unavailable; selected a compatible safe action.", action, request.getPrompt(), "Observe the tool result and then decide whether to finish.");
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

    private boolean contains(String input, String... values) { for (String value : values) if (input.contains(value)) return true; return false; }
    private String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }

    public record GeneratedDecision(ReActDecision decision, boolean modelGenerated, String provider) { }
}
