package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

/** Immutable audit record for each plan generated during one Plan-Solve run. */
@Data
@Table(value = "agent_plan_revision", mapperGenerateEnable = false)
public class AgentPlanRevisionEntity {
    private String runId;
    private Integer revisionNo;
    private Integer parentRevisionNo;
    private String reason;
    private String planJson;
    private String stateSummaryJson;
    private Timestamp createdAt;
}