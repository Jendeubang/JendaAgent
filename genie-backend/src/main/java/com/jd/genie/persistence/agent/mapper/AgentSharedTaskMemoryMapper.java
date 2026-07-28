package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentSharedTaskMemoryEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentSharedTaskMemoryMapper extends BaseMapper<AgentSharedTaskMemoryEntity> {
    @Insert("INSERT INTO agent_shared_task_memory (run_id, task_id, tool_name, state, attempt, input_json, output_json, asset_ids_json, failure_reason, summary, updated_at) VALUES (#{runId}, #{taskId}, #{toolName}, #{state}, #{attempt}, #{inputJson}, #{outputJson}, #{assetIdsJson}, #{failureReason}, #{summary}, #{updatedAt}) ON DUPLICATE KEY UPDATE tool_name=#{toolName}, state=#{state}, attempt=#{attempt}, input_json=#{inputJson}, output_json=#{outputJson}, asset_ids_json=#{assetIdsJson}, failure_reason=#{failureReason}, summary=#{summary}, updated_at=#{updatedAt}")
    int upsert(AgentSharedTaskMemoryEntity entity);

    @Select("SELECT * FROM agent_shared_task_memory WHERE run_id = #{runId} ORDER BY updated_at, task_id")
    List<AgentSharedTaskMemoryEntity> findByRun(@Param("runId") String runId);
}