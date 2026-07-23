package com.jd.genie.service.agent.plansolve;

/** Persisted state of a DAG node. Values are intentionally database-friendly. */
public enum PlanTaskState {
    PENDING,
    RUNNING,
    WAITING_CONFIRMATION,
    COMPLETE,
    FAILED,
    SKIPPED
}
