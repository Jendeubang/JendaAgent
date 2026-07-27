package com.jd.genie.model.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AgentPlanUpdateRequest(@NotBlank @Pattern(regexp = "FREE|PRO|ADMIN") String planCode) {
}