package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentPlanTaskStateEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AgentPlanTaskStateMapper extends BaseMapper<AgentPlanTaskStateEntity> {
    @Insert("INSERT INTO agent_plan_task_state (run_id, task_id, state, attempts, result_json, updated_at) VALUES (#{runId}, #{taskId}, #{state}, #{attempts}, #{resultJson}, #{updatedAt})")
    int insertTask(AgentPlanTaskStateEntity entity);

    @Select("SELECT * FROM agent_plan_task_state WHERE run_id = #{runId}")
    List<AgentPlanTaskStateEntity> findByRun(@Param("runId") String runId);

    @Update("UPDATE agent_plan_task_state SET state = #{state}, attempts = #{attempts}, result_json = #{resultJson}, updated_at = #{updatedAt} WHERE run_id = #{runId} AND task_id = #{taskId}")
    int updateTask(@Param("runId") String runId, @Param("taskId") String taskId, @Param("state") String state, @Param("attempts") int attempts, @Param("resultJson") String resultJson, @Param("updatedAt") java.sql.Timestamp updatedAt);
}