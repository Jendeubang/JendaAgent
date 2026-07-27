package com.jd.genie.persistence.agent.entity;

import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Table(value = "agent_asset", mapperGenerateEnable = false)
public class AgentAssetEntity {
    private String assetId;
    private String ownerUserId;
    private String sessionId;
    private String runId;
    private String fileName;
    private String mediaType;
    private Long sizeBytes;
    private String objectKey;
    private String imageUrl;
    private String source;
    private Timestamp createdAt;
}