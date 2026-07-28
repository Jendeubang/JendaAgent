package com.jd.genie.persistence.agent.service;

import com.jd.genie.persistence.agent.entity.AgentPlanApprovalEntity;
import com.jd.genie.persistence.agent.entity.AgentPlanExecutionEntity;
import com.jd.genie.persistence.agent.entity.AgentPlanRevisionEntity;
import com.jd.genie.persistence.agent.entity.AgentPlanTaskStateEntity;
import com.jd.genie.persistence.agent.mapper.AgentPlanApprovalMapper;
import com.jd.genie.persistence.agent.mapper.AgentPlanExecutionMapper;
import com.jd.genie.persistence.agent.mapper.AgentPlanRevisionMapper;
import com.jd.genie.persistence.agent.mapper.AgentPlanTaskStateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** MyBatis-Flex persistence boundary for resumable and versioned Plan-Solve DAG state. */
@Service
@RequiredArgsConstructor
public class AgentPlanPersistenceService {
    private final AgentPlanExecutionMapper executionMapper;
    private final AgentPlanTaskStateMapper taskMapper;
    private final AgentPlanApprovalMapper approvalMapper;
    private final AgentPlanRevisionMapper revisionMapper;

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void create(AgentPlanExecutionEntity execution, List<AgentPlanTaskStateEntity> tasks, AgentPlanRevisionEntity revision) {
        executionMapper.insertExecution(execution);
        tasks.forEach(taskMapper::insertTask);
        revisionMapper.insertRevision(revision);
    }

    public AgentPlanExecutionEntity load(String ownerUserId, String sessionId, String runId) {
        return executionMapper.findOwned(ownerUserId, sessionId, runId);
    }

    public List<AgentPlanTaskStateEntity> taskStates(String runId) { return taskMapper.findByRun(runId); }

    public int latestRevisionNo(String runId) { return revisionMapper.latestRevisionNo(runId); }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void replacePlan(String runId, String planJson, List<AgentPlanTaskStateEntity> tasks, AgentPlanRevisionEntity revision) {
        executionMapper.replacePlan(runId, planJson, "RUNNING", now());
        tasks.forEach(taskMapper::insertTask);
        revisionMapper.insertRevision(revision);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void saveTask(String runId, String taskId, String state, int attempts, String resultJson) {
        taskMapper.updateTask(runId, taskId, state, attempts, resultJson, now());
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public String requestApproval(String runId, String taskId, String ownerUserId, String message, String approvalId) {
        String existing = approvalMapper.findWaiting(runId, taskId);
        if (existing != null) return existing;
        AgentPlanApprovalEntity approval = new AgentPlanApprovalEntity();
        approval.setApprovalId(approvalId);
        approval.setRunId(runId);
        approval.setTaskId(taskId);
        approval.setOwnerUserId(ownerUserId);
        approval.setMessage(message);
        approval.setStatus("WAITING");
        approval.setCreatedAt(now());
        approvalMapper.insertApproval(approval);
        return approvalId;
    }

    public AgentPlanApprovalEntity findApproval(String ownerUserId, String runId, String approvalId) {
        return approvalMapper.findOwned(ownerUserId, runId, approvalId);
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void resolveApproval(String ownerUserId, String approvalId, boolean approved, String note) {
        approvalMapper.resolve(ownerUserId, approvalId, approved ? "APPROVED" : "REJECTED", note, now());
    }

    @Transactional(transactionManager = "agentPersistenceTransactionManager")
    public void updateRunStatus(String runId, String status) { executionMapper.updateStatus(runId, status, now()); }

    private Timestamp now() { return Timestamp.from(Instant.now()); }
}