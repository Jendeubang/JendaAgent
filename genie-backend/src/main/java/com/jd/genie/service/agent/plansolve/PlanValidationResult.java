package com.jd.genie.service.agent.plansolve;

import java.util.List;

public record PlanValidationResult(boolean valid, List<String> errors) {
    public PlanValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static PlanValidationResult success() { return new PlanValidationResult(true, List.of()); }
}
