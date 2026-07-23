package com.jd.genie.model.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DirectUploadCompleteRequest {
    @NotBlank
    private String uploadId;
}
