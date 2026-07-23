package com.jd.genie.service.agent.react;

/** Model decision persisted at the end of every Think phase. */
public record ReActDecision(String reasoning, ReActAction action, String toolInput, String nextDecision) {
    public ReActDecision {
        reasoning = reasoning == null ? "" : reasoning;
        toolInput = toolInput == null ? "" : toolInput;
        nextDecision = nextDecision == null ? "" : nextDecision;
    }
}
