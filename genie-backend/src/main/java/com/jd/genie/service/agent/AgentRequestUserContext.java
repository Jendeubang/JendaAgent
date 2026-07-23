package com.jd.genie.service.agent;

import com.jd.genie.model.auth.AgentPrincipal;

import java.util.function.Supplier;

/** Carries the request owner into the synchronous run creation path. */
public final class AgentRequestUserContext {
    private static final ThreadLocal<AgentPrincipal> CURRENT = new ThreadLocal<>();
    private static final AgentPrincipal LOCAL_DEMO = new AgentPrincipal("local-demo-user", "local-demo");

    private AgentRequestUserContext() {
    }

    public static AgentPrincipal current() {
        AgentPrincipal principal = CURRENT.get();
        return principal == null ? LOCAL_DEMO : principal;
    }

    public static <T> T runAs(AgentPrincipal principal, Supplier<T> action) {
        CURRENT.set(principal);
        try {
            return action.get();
        } finally {
            CURRENT.remove();
        }
    }
}
