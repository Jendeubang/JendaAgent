package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentPlanApprovalEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AgentPlanApprovalMapper extends BaseMapper<AgentPlanApprovalEntity> {
    @Select("SELECT approval_id FROM agent_plan_approval WHERE run_id = #{runId} AND task_id = #{taskId} AND status = 'WAITING' ORDER BY created_at DESC LIMIT 1")
    String findWaiting(@Param("runId") String runId, @Param("taskId") String taskId);

    @Insert("INSERT INTO agent_plan_approval (approval_id, run_id, task_id, owner_user_id, message, status, note, created_at, resolved_at) VALUES (#{approvalId}, #{runId}, #{taskId}, #{ownerUserId}, #{message}, #{status}, #{note}, #{createdAt}, #{resolvedAt})")
    int insertApproval(AgentPlanApprovalEntity entity);

    @Select("SELECT * FROM agent_plan_approval WHERE approval_id = #{approvalId} AND run_id = #{runId} AND owner_user_id = #{ownerUserId}")
    AgentPlanApprovalEntity findOwned(@Param("ownerUserId") String ownerUserId, @Param("runId") String runId, @Param("approvalId") String approvalId);

    @Update("UPDATE agent_plan_approval SET status = #{status}, note = #{note}, resolved_at = #{resolvedAt} WHERE approval_id = #{approvalId} AND owner_user_id = #{ownerUserId}")
    int resolve(@Param("ownerUserId") String ownerUserId, @Param("approvalId") String approvalId, @Param("status") String status, @Param("note") String note, @Param("resolvedAt") java.sql.Timestamp resolvedAt);
}