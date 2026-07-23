package com.jd.genie.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AgentSmsCodeRequest(
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotNull AgentSmsPurpose purpose) {
}
