package com.jd.genie.persistence.agent.mapper;

import com.jd.genie.persistence.agent.entity.AgentPlanRevisionEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AgentPlanRevisionMapper extends BaseMapper<AgentPlanRevisionEntity> {
    @Insert("INSERT INTO agent_plan_revision (run_id, revision_no, parent_revision_no, reason, plan_json, state_summary_json, created_at) VALUES (#{runId}, #{revisionNo}, #{parentRevisionNo}, #{reason}, #{planJson}, #{stateSummaryJson}, #{createdAt})")
    int insertRevision(AgentPlanRevisionEntity entity);

    @Select("SELECT COALESCE(MAX(revision_no), 0) FROM agent_plan_revision WHERE run_id = #{runId}")
    int latestRevisionNo(@Param("runId") String runId);
}