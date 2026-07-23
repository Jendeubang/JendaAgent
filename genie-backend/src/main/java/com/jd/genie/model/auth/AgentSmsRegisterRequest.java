package com.jd.genie.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentSmsRegisterRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Pattern(regexp = "1[3-9]\\d{9}") String phone,
        @NotBlank @Pattern(regexp = "\\d{6}") String code) {
}
