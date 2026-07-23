package com.jd.genie.service.agent;

public record AgentToolResult(
        AgentToolType tool,
        boolean invoked,
        boolean success,
        String summary,
        String imageUrl,
        String provider) {
    public static AgentToolResult skipped(AgentToolType tool, String reason) {
        return new AgentToolResult(tool, false, false, reason, null, "not-configured");
    }
}
