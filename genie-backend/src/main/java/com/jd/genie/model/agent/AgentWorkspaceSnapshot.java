package com.jd.genie.model.agent;

import java.util.List;

/** Session summary and the visual assets required by the Workspace panel. */
public record AgentWorkspaceSnapshot(
        AgentSessionOverview session,
        List<AgentWorkspaceAsset> assets) {
}
