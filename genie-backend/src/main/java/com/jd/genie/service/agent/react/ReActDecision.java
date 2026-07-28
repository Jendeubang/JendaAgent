package com.jd.genie.service.agent.react;

import java.util.List;

/** Model decision persisted at the end of every Think phase. */
public record ReActDecision(String reasoning, ReActAction action, String toolInput, String nextDecision,
                            String imageProvider, List<ReActToolInvocation> parallelActions) {
    public ReActDecision {
        reasoning = reasoning == null ? "" : reasoning;
        toolInput = toolInput == null ? "" : toolInput;
        nextDecision = nextDecision == null ? "" : nextDecision;
        imageProvider = imageProvider == null ? "" : imageProvider;
        parallelActions = parallelActions == null ? List.of() : List.copyOf(parallelActions);
    }
}