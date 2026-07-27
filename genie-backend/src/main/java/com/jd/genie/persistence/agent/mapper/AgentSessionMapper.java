package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentSessionEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentSessionMapper extends BaseMapper<AgentSessionEntity> {
    @Select("SELECT * FROM agent_session WHERE session_id = #{sessionId}")
    AgentSessionEntity findById(@Param("sessionId") String sessionId);

    @Select("SELECT owner_user_id FROM agent_session_claim WHERE session_id = #{sessionId}")
    String findClaimOwner(@Param("sessionId") String sessionId);

    @Insert("INSERT INTO agent_session_claim (session_id, owner_user_id, claimed_at) VALUES (#{sessionId}, #{ownerUserId}, #{claimedAt})")
    int insertClaim(@Param("sessionId") String sessionId, @Param("ownerUserId") String ownerUserId, @Param("claimedAt") java.sql.Timestamp claimedAt);

    @Insert("INSERT INTO agent_session (session_id, owner_user_id, latest_run_id, mode, latest_prompt, run_status, created_at, updated_at) VALUES (#{sessionId}, #{ownerUserId}, #{latestRunId}, #{mode}, #{latestPrompt}, #{runStatus}, #{createdAt}, #{updatedAt})")
    int insertSession(AgentSessionEntity entity);

    @Update("UPDATE agent_session SET latest_run_id = #{runId}, mode = #{mode}, latest_prompt = #{prompt}, run_status = #{status}, updated_at = #{updatedAt} WHERE session_id = #{sessionId} AND owner_user_id = #{ownerUserId}")
    int updateRun(@Param("sessionId") String sessionId, @Param("ownerUserId") String ownerUserId, @Param("runId") String runId, @Param("mode") String mode, @Param("prompt") String prompt, @Param("status") String status, @Param("updatedAt") java.sql.Timestamp updatedAt);

    @Update("UPDATE agent_session SET run_status = #{status}, updated_at = #{updatedAt} WHERE session_id = #{sessionId}")
    int updateStatus(@Param("sessionId") String sessionId, @Param("status") String status, @Param("updatedAt") java.sql.Timestamp updatedAt);
}