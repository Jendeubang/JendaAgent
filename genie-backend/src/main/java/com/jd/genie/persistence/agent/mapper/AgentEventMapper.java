package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentEventEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AgentEventMapper extends BaseMapper<AgentEventEntity> {
    @Insert("INSERT INTO agent_event (event_id, session_id, run_id, sequence_no, event_type, status, agent_name, occurred_at, payload_json) VALUES (#{eventId}, #{sessionId}, #{runId}, #{sequenceNo}, #{eventType}, #{status}, #{agentName}, #{occurredAt}, #{payloadJson})")
    int insertEvent(AgentEventEntity entity);

    @Select("SELECT event_id, session_id, run_id, sequence_no, event_type, status, agent_name, occurred_at, payload_json FROM agent_event WHERE session_id = #{sessionId} UNION ALL SELECT event_id, session_id, run_id, sequence_no, message_type AS event_type, status, agent_name, occurred_at, payload_json FROM agent_message WHERE session_id = #{sessionId} ORDER BY occurred_at, sequence_no")
    List<AgentEventEntity> replay(@Param("sessionId") String sessionId);

    @Select("SELECT COALESCE(MAX(sequence_no), 0) FROM (SELECT sequence_no FROM agent_event WHERE run_id = #{runId} UNION ALL SELECT sequence_no FROM agent_message WHERE run_id = #{runId}) agent_sequences")
    Long lastSequence(@Param("runId") String runId);
}