package com.jd.genie.model.agent;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentEventStatus {
    QUEUED("queued"),
    RUNNING("running"),
    COMPLETE("complete"),
    FAILED("failed");

    private final String value;

    AgentEventStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
