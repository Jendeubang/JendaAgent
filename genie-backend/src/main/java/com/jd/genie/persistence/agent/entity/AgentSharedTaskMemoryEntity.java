package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

/** Durable shared context produced by an individual Plan-Solve task. */
@Data
@Table(value = "agent_shared_task_memory", mapperGenerateEnable = false)
public class AgentSharedTaskMemoryEntity {
    private String runId;
    private String taskId;
    private String toolName;
    private String state;
    private Integer attempt;
    private String inputJson;
    private String outputJson;
    private String assetIdsJson;
    private String failureReason;
    private String summary;
    private Timestamp updatedAt;
}