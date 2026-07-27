package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_plan_execution", mapperGenerateEnable = false)
public class AgentPlanExecutionEntity {
    private String runId;
    private String sessionId;
    private String ownerUserId;
    private String planJson;
    private String requestJson;
    private String status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}