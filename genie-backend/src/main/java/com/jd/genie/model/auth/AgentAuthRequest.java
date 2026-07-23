package com.jd.genie.model.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgentAuthRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,32}") String username,
        @NotBlank @Size(min = 8, max = 128) String password) {
}
