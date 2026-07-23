package com.jd.genie.model.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentRunMode {
    PLAN_SOLVE("plan-solve"),
    REACT("react");

    private final String value;

    AgentRunMode(String value) {
        this.value = value;
    }

    @JsonCreator
    public static AgentRunMode fromValue(String value) {
        for (AgentRunMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported agent mode: " + value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
