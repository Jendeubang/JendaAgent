package com.jd.genie.model.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DirectUploadTicketRequest {
    @NotBlank
    private String sessionId;

    @NotBlank
    private String fileName;

    @NotBlank
    private String mediaType;

    @NotNull
    @Min(1)
    @Max(10 * 1024 * 1024)
    private Long size;
}
