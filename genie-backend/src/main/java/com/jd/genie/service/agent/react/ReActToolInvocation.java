package com.jd.genie.service.agent.react;

/** One independent action inside an explicit ReAct PARALLEL decision. */
public record ReActToolInvocation(ReActAction action, String toolInput, String imageProvider) {
    public ReActToolInvocation {
        toolInput = toolInput == null ? "" : toolInput;
        imageProvider = imageProvider == null ? "" : imageProvider;
    }
}