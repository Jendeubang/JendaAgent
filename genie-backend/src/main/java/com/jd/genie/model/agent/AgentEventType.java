package com.jd.genie.model.agent;

import com.fasterxml.jackson.annotation.JsonValue;

/** Stable event names consumed by the agent workspace and persisted by message type. */
public enum AgentEventType {
    RUN_STARTED("run_started"),
    PLAN("plan"),
    PROMPT_OPTIMIZATION("prompt_optimization"),
    TASK("task"),
    TOOL_CALL("tool_call"),
    TOOL_RESULT("tool_result"),
    IMAGE("image"),
    SUMMARY("summary"),
    CONFIRMATION_REQUIRED("confirmation_required"),
    REACT_THINK("react_think"),
    REACT_ACT("react_act"),
    REACT_OBSERVATION("react_observation"),
    REACT_DECISION("react_decision"),
    REACT_TERMINATED("react_terminated"),
    RUN_COMPLETED("run_completed"),
    HEARTBEAT("heartbeat"),
    ERROR("error");

    private final String value;
    AgentEventType(String value) { this.value = value; }
    @JsonValue public String getValue() { return value; }
}