package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_event", mapperGenerateEnable = false)
public class AgentEventEntity {
    private String eventId;
    private String sessionId;
    private String runId;
    private Long sequenceNo;
    private String eventType;
    private String status;
    private String agentName;
    private Timestamp occurredAt;
    private String payloadJson;
}