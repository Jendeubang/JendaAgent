package com.jd.genie.service.agent;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Returned when one user attempts to access another user's agent session. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AgentSessionAccessDeniedException extends RuntimeException {
    public AgentSessionAccessDeniedException() {
        super("The current user does not own this agent session");
    }
}
