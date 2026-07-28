package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentPlanExecutionEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentPlanExecutionMapper extends BaseMapper<AgentPlanExecutionEntity> {
    @Insert("INSERT INTO agent_plan_execution (run_id, session_id, owner_user_id, plan_json, request_json, status, created_at, updated_at) VALUES (#{runId}, #{sessionId}, #{ownerUserId}, #{planJson}, #{requestJson}, #{status}, #{createdAt}, #{updatedAt})")
    int insertExecution(AgentPlanExecutionEntity entity);

    @Select("SELECT * FROM agent_plan_execution WHERE run_id = #{runId} AND session_id = #{sessionId} AND owner_user_id = #{ownerUserId}")
    AgentPlanExecutionEntity findOwned(@Param("ownerUserId") String ownerUserId, @Param("sessionId") String sessionId, @Param("runId") String runId);

    @Update("UPDATE agent_plan_execution SET status = #{status}, updated_at = #{updatedAt} WHERE run_id = #{runId}")
    int updateStatus(@Param("runId") String runId, @Param("status") String status, @Param("updatedAt") java.sql.Timestamp updatedAt);

    @Update("UPDATE agent_plan_execution SET plan_json = #{planJson}, status = #{status}, updated_at = #{updatedAt} WHERE run_id = #{runId}")
    int replacePlan(@Param("runId") String runId, @Param("planJson") String planJson, @Param("status") String status, @Param("updatedAt") java.sql.Timestamp updatedAt);
}