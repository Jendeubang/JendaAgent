package com.jd.genie.service.agent;

public enum AgentToolType {
    OCR("OCR"),
    IMAGE_GENERATE("图像生成"),
    IMAGE_EDIT("图像编辑");

    private final String displayName;

    AgentToolType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
