package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_run", mapperGenerateEnable = false)
public class AgentRunEntity {
    private String runId;
    private String sessionId;
    private String ownerUserId;
    private String mode;
    private String prompt;
    private String imageUrlsJson;
    private String status;
    private Timestamp createdAt;
    private Timestamp completedAt;
}