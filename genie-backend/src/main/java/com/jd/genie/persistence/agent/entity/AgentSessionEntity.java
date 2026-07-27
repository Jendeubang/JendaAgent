package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_session", mapperGenerateEnable = false)
public class AgentSessionEntity {
    private String sessionId;
    private String ownerUserId;
    private String latestRunId;
    private String mode;
    private String latestPrompt;
    private String runStatus;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}