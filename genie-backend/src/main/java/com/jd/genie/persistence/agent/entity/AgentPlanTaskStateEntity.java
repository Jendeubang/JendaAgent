package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_plan_task_state", mapperGenerateEnable = false)
public class AgentPlanTaskStateEntity {
    private String runId;
    private String taskId;
    private String state;
    private Integer attempts;
    private String resultJson;
    private Timestamp updatedAt;
}