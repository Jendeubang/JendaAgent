package com.jd.genie.service.agent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.model.agent.AgentRunRequest;
import com.jd.genie.service.agent.ModelCompletion;
import com.jd.genie.service.agent.OpenAiCompatibleChatClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Produces bounded sequential or explicitly independent parallel ReAct decisions. */
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
                if (decision.action() != null) return new GeneratedDecision(continueRequiredWork(normalize(decision, request), request, observations), true, completion.provider());
            } catch (Exception ignored) {
                // Continue with a deterministic safe decision below.
            }
        }
        return new GeneratedDecision(continueRequiredWork(fallback(request, observations), request, observations), false, completion.provider());
    }

    private String systemPrompt() {
        return "You are a bounded ReAct agent for an image AI product. Return only JSON: "
                + "{reasoning:string,action:'OCR'|'IMAGE_GENERATE'|'IMAGE_EDIT'|'PARALLEL'|'PLAN_SOLVE'|'FINISH',toolInput:string,nextDecision:string,imageProvider:string,parallelActions:[{action:'OCR'|'IMAGE_GENERATE'|'IMAGE_EDIT',toolInput:string,imageProvider:string}]}. "
                + "Reasoning is a brief operational summary, not hidden chain-of-thought. Choose one action only. "
                + "Use PARALLEL only for exactly two independent calls whose inputs do not depend on each other; never use duplicate tool types and never use IMAGE_GENERATE with IMAGE_EDIT in the same group. "
                + "Do not choose IMAGE_EDIT or OCR without an input image. Choose FINISH only when observations satisfy the task.";
    }

    private String userPrompt(AgentRunRequest request, int round, String observations) {
        return "User task: " + request.getPrompt() + "\nRound: " + round + "\nImage count: " + request.getImageUrls().size()
                + "\nPrior observations: " + (observations.isBlank() ? "none" : observations)
                + "\nAllowed actions: OCR, IMAGE_GENERATE, IMAGE_EDIT, PARALLEL, PLAN_SOLVE, FINISH";
    }

    private ReActDecision normalize(ReActDecision decision, AgentRunRequest request) {
        if (decision.action() == ReActAction.PARALLEL) return normalizeParallel(decision, request);
        if (decision.action() == ReActAction.PLAN_SOLVE) {
            return new ReActDecision(limit(decision.reasoning(), 800), ReActAction.PLAN_SOLVE, "", limit(decision.nextDecision(), 800), "", List.of());
        }
        String input = decision.toolInput().isBlank() ? request.getPrompt() : decision.toolInput();
        if ((decision.action() == ReActAction.OCR || decision.action() == ReActAction.IMAGE_EDIT) && request.getImageUrls().isEmpty()) {
            return finish("Input image is required for the requested action.", "Stop because no compatible input image is available.");
        }
        return new ReActDecision(limit(decision.reasoning(), 800), decision.action(), limit(input, 8000), limit(decision.nextDecision(), 800), limit(decision.imageProvider(), 32), List.of());
    }

    private ReActDecision normalizeParallel(ReActDecision decision, AgentRunRequest request) {
        List<ReActToolInvocation> normalized = new ArrayList<>();
        Set<ReActAction> actions = new HashSet<>();
        for (ReActToolInvocation call : decision.parallelActions()) {
            if (call == null || call.action() == null || call.action() == ReActAction.PARALLEL || call.action() == ReActAction.FINISH) continue;
            if (actions.contains(call.action())) continue;
            if ((call.action() == ReActAction.OCR || call.action() == ReActAction.IMAGE_EDIT) && request.getImageUrls().isEmpty()) continue;
            String input = call.toolInput().isBlank() ? request.getPrompt() : call.toolInput();
            actions.add(call.action());
            normalized.add(new ReActToolInvocation(call.action(), limit(input, 8000), limit(call.imageProvider(), 32)));
        }
        boolean mixesGenerateAndEdit = actions.contains(ReActAction.IMAGE_GENERATE) && actions.contains(ReActAction.IMAGE_EDIT);
        if (normalized.size() != 2 || mixesGenerateAndEdit) return fallback(request, "");
        return new ReActDecision(limit(decision.reasoning(), 800), ReActAction.PARALLEL, "", limit(decision.nextDecision(), 800), "", normalized);
    }

    private ReActDecision fallback(AgentRunRequest request, String observations) {
        if (!observations.isBlank()) return finish("A tool observation is available and the next step is delivery.", "Generate the final delivery summary.");
        String prompt = request.getPrompt().toLowerCase(Locale.ROOT);
        ReActAction action;
        if (!request.getImageUrls().isEmpty() && contains(prompt, "ocr", "extract text", "read text", "\u8bc6\u522b", "\u6587\u5b57")) action = ReActAction.OCR;
        else if (!request.getImageUrls().isEmpty() && contains(prompt, "edit", "background", "replace", "style", "\u7f16\u8f91", "\u80cc\u666f", "\u66ff\u6362")) action = ReActAction.IMAGE_EDIT;
        else action = ReActAction.IMAGE_GENERATE;
        return new ReActDecision("Model decision is unavailable; selected a compatible safe action.", action, request.getPrompt(), "Observe the tool result and then decide whether to finish.", request.getImageProvider(), List.of());
    }

    private ReActDecision continueRequiredWork(ReActDecision decision, AgentRunRequest request, String observations) {
        if (decision.action() != ReActAction.FINISH || !needsPosterAfterOcr(request.getPrompt(), observations)) return decision;
        String prompt = request.getPrompt() + "\nOCR extracted copy:\n" + observations;
        return new ReActDecision("OCR completed; the requested poster generation remains pending.", ReActAction.IMAGE_GENERATE,
                limit(prompt, 8000), "Generate the requested visual using the extracted copy, then summarize the delivery.", request.getImageProvider(), List.of());
    }

    private ReActDecision finish(String reasoning, String next) { return new ReActDecision(reasoning, ReActAction.FINISH, "", next, "", List.of()); }
    private boolean needsPosterAfterOcr(String prompt, String observations) { String p = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT); String o = observations == null ? "" : observations.toLowerCase(Locale.ROOT); return o.contains("ocr:") && !o.contains("image_generate:") && contains(p, "ocr", "extract text", "read text", "\u8bc6\u522b", "\u63d0\u53d6\u6587\u5b57", "\u6587\u6848") && contains(p, "generate", "create", "poster", "image", "\u751f\u6210", "\u6d77\u62a5", "\u5ba3\u4f20\u56fe", "\u914d\u56fe", "\u56fe\u7247"); }
    private String extractJson(String value) { String candidate = value == null ? "" : value.trim(); if (candidate.startsWith("```")) { int lineEnd = candidate.indexOf('\n'); int close = candidate.lastIndexOf("```"); candidate = lineEnd >= 0 && close > lineEnd ? candidate.substring(lineEnd + 1, close).trim() : candidate; } int start = candidate.indexOf('{'); int end = candidate.lastIndexOf('}'); return start >= 0 && end > start ? candidate.substring(start, end + 1) : candidate; }
    private boolean contains(String input, String... values) { for (String value : values) if (input.contains(value)) return true; return false; }
    private String limit(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max); }
    public record GeneratedDecision(ReActDecision decision, boolean modelGenerated, String provider) { }
}