package com.jd.genie.model.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AgentRunRequest {
    @NotBlank(message = "prompt must not be blank")
    @Size(max = 8000, message = "prompt must not exceed 8000 characters")
    private String prompt;

    @NotNull(message = "mode must not be null")
    private AgentRunMode mode;

    @Size(max = 10, message = "at most 10 image URLs are supported")
    private List<String> imageUrls = new ArrayList<>();
    @Size(max = 3, message = "at most 3 preferred tools are supported")
    private List<String> preferredTools = new ArrayList<>();

    /** Optional image provider override. Omit it to use agent.image-provider.default-provider. */
    @Size(max = 32, message = "image provider must not exceed 32 characters")
    private String imageProvider;
}
