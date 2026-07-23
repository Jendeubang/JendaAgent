package com.jd.genie.model.agent;

import java.util.List;

/** Paginated, user-scoped COS asset collection. */
public record AgentAssetPage(List<AgentAssetMetadata> items, long total, int page, int size) {
}
