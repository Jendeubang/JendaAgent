package com.jd.genie.model.agent;

import java.time.Instant;
import java.util.Map;

/**
 * Versioned SSE envelope. The payload is type-specific and can be routed to a child history table.
 */
public record AgentEvent(
        String schemaVersion,
        String eventId,
        String sessionId,
        String runId,
        long sequence,
        AgentEventType messageType,
        AgentEventStatus status,
        String agent,
        Instant occurredAt,
        Map<String, Object> payload) {
}
