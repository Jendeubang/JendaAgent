package com.jd.genie.model.auth;

import jakarta.validation.constraints.NotBlank;

public record AgentRefreshRequest(@NotBlank String refreshToken) {
}
