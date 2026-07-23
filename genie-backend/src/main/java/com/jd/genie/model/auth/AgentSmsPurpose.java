package com.jd.genie.model.auth;

/** A verification code can only be consumed by the flow that issued it. */
public enum AgentSmsPurpose {
    REGISTER,
    RESET_PASSWORD
}
