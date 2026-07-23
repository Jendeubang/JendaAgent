package com.jd.genie.service.agent;

import com.jd.genie.config.PromptOpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/** Prompt-optimization sub-agent for image generation and editing. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptOpAgent {
    private final PromptOpProperties properties;
    private final PromptKnowledgeRetriever knowledgeRetriever;
    private final OpenAiCompatibleChatClient chatClient;

    public PromptOptimization optimize(String originalPrompt, List<AgentToolType> tools, List<String> imageUrls, boolean requested) {
        if (!requested) return PromptOptimization.skipped(originalPrompt, "Prompt optimization was disabled for this request");
        if (!properties.isEnabled()) return PromptOptimization.skipped(originalPrompt, "PromptOpAgent is disabled");
        List<AgentToolType> imageTools = tools.stream().filter(tool -> tool == AgentToolType.IMAGE_GENERATE || tool == AgentToolType.IMAGE_EDIT).toList();
        if (imageTools.isEmpty()) return PromptOptimization.skipped(originalPrompt, "No image tool is scheduled");
        List<PromptKnowledgeHit> hits = knowledgeRetriever.retrieve(originalPrompt, imageTools, properties.getMaxKnowledgeItems());
        List<PromptKnowledgeSnippet> snippets = hits.stream().map(PromptKnowledgeHit::snippet).toList();
        List<String> ruleIds = snippets.stream().map(PromptKnowledgeSnippet::id).toList();
        String knowledgeVersion = hits.isEmpty() ? knowledgeRetriever.knowledgeVersion() : hits.get(0).knowledgeVersion();
        boolean ragUsed = hits.stream().anyMatch(PromptKnowledgeHit::vectorRetrieved);
        try {
            ModelCompletion completion = chatClient.complete(systemPrompt(imageTools), userPrompt(originalPrompt, imageTools, snippets), imageUrls);
            if (!completion.invoked()) return new PromptOptimization(false, originalPrompt, originalPrompt, ruleIds, hits, knowledgeVersion, ragUsed, completion.provider(), completion.content());
            String optimized = normalize(completion.content(), originalPrompt);
            return new PromptOptimization(true, originalPrompt, optimized, ruleIds, hits, knowledgeVersion, ragUsed, completion.provider(), "Prompt optimized with retrieved guidelines");
        } catch (Exception error) {
            log.warn("PromptOpAgent fell back to the original prompt: {}", error.getMessage());
            return new PromptOptimization(false, originalPrompt, originalPrompt, ruleIds, hits, knowledgeVersion, ragUsed, "fallback", "Prompt optimization failed; original prompt retained");
        }
    }

    private String systemPrompt(List<AgentToolType> tools) {
        boolean editing = tools.contains(AgentToolType.IMAGE_EDIT);
        return "You are PromptOpAgent. Rewrite the user's request as one precise production image prompt. "
                + "Preserve every explicit user requirement. Do not claim tools or steps that are unavailable. "
                + "Return only the optimized prompt, with no heading, explanation, markdown, or quotation marks. "
                + (editing ? "This is an image-edit task. Preserve unmentioned regions and identity. If multiple images exist, respect their order and any mask instruction."
                : "This is a text-to-image task. Include subject, environment, composition, lighting, palette, style, and quality only when relevant.");
    }

    private String userPrompt(String originalPrompt, List<AgentToolType> tools, List<PromptKnowledgeSnippet> snippets) {
        String guidelines = snippets.isEmpty() ? "No matching guidelines were retrieved." : snippets.stream().map(snippet -> "[" + snippet.id() + "] " + snippet.guidance()).reduce((left, right) -> left + "\n" + right).orElse("No matching guidelines were retrieved.");
        return "Tool types: " + tools + "\nOriginal request:\n" + originalPrompt + "\n\nRetrieved prompt guidelines:\n" + guidelines;
    }

    private String normalize(String candidate, String fallback) {
        String value = candidate == null ? "" : candidate.trim().replaceAll("^['\\\"]|['\\\"]$", "");
        if (value.length() < 12) return fallback;
        int maxLength = Math.max(200, properties.getMaxPromptLength());
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}