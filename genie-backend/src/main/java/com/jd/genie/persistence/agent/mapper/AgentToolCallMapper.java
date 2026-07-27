package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentToolCallEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentToolCallMapper extends BaseMapper<AgentToolCallEntity> {
    @Insert("INSERT INTO agent_tool_call (tool_call_id, session_id, run_id, owner_user_id, tool_name, call_event_id, status, request_json, started_at) VALUES (#{toolCallId}, #{sessionId}, #{runId}, #{ownerUserId}, #{toolName}, #{callEventId}, #{status}, #{requestJson}, #{startedAt})")
    int insertCall(AgentToolCallEntity entity);

    @Select("SELECT tool_call_id FROM agent_tool_call WHERE run_id = #{runId} AND tool_name = #{toolName} AND status = 'running' ORDER BY started_at DESC LIMIT 1")
    String findLatestRunning(@Param("runId") String runId, @Param("toolName") String toolName);

    @Update("UPDATE agent_tool_call SET result_event_id = #{resultEventId}, status = #{status}, result_json = #{resultJson}, completed_at = #{completedAt} WHERE tool_call_id = #{toolCallId}")
    int complete(@Param("toolCallId") String toolCallId, @Param("resultEventId") String resultEventId, @Param("status") String status, @Param("resultJson") String resultJson, @Param("completedAt") java.sql.Timestamp completedAt);
}