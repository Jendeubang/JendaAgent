package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentRunEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentRunMapper extends BaseMapper<AgentRunEntity> {
    @Insert("INSERT INTO agent_run (run_id, session_id, owner_user_id, mode, prompt, image_urls_json, status, created_at, completed_at) VALUES (#{runId}, #{sessionId}, #{ownerUserId}, #{mode}, #{prompt}, #{imageUrlsJson}, #{status}, #{createdAt}, #{completedAt})")
    int insertRun(AgentRunEntity entity);

    @Select("SELECT owner_user_id FROM agent_run WHERE run_id = #{runId}")
    String findOwner(@Param("runId") String runId);

    @Update("UPDATE agent_run SET status = #{status}, completed_at = #{completedAt} WHERE run_id = #{runId}")
    int complete(@Param("runId") String runId, @Param("status") String status, @Param("completedAt") java.sql.Timestamp completedAt);

    @Update("UPDATE agent_run SET status = #{status} WHERE run_id = #{runId} AND session_id = #{sessionId}")
    int updateStatus(@Param("sessionId") String sessionId, @Param("runId") String runId, @Param("status") String status);
}