package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_tool_call", mapperGenerateEnable = false)
public class AgentToolCallEntity {
    private String toolCallId;
    private String sessionId;
    private String runId;
    private String ownerUserId;
    private String toolName;
    private String callEventId;
    private String resultEventId;
    private String status;
    private String requestJson;
    private String resultJson;
    private Timestamp startedAt;
    private Timestamp completedAt;
}