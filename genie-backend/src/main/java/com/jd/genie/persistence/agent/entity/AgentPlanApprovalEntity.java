package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_plan_approval", mapperGenerateEnable = false)
public class AgentPlanApprovalEntity {
    private String approvalId;
    private String runId;
    private String taskId;
    private String ownerUserId;
    private String message;
    private String status;
    private String note;
    private Timestamp createdAt;
    private Timestamp resolvedAt;
}