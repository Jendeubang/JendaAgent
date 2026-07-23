package com.jd.genie.model.agent;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Decision supplied by the authenticated operator for a paused DAG node. */
public record PlanSolveApprovalRequest(
        @NotNull Boolean approved,
        @Size(max = 1000) String note) {
}