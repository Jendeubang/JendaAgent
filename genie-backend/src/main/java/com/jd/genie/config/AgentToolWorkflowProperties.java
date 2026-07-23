package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Server-side configuration for the real image-tool workflows. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.tools")
public class AgentToolWorkflowProperties {
    private boolean fallbackToImageProvider = true;
    private String localPublicBaseUrl = "";
    private Endpoint imageUpscale = new Endpoint();
    private Endpoint seedvr2 = new Endpoint();
    private Endpoint imageLayered = new Endpoint();
    private Endpoint productRefinement = new Endpoint();
    private Endpoint productDetailImage = new Endpoint();
    private Endpoint ecommercePromotionPoster = new Endpoint();
    private Endpoint characterSettingSheet = new Endpoint();
    private Endpoint emojiSticker = new Endpoint();

    public Endpoint forTool(String toolId) {
        return switch (toolId) {
            case "image-upscale" -> imageUpscale;
            case "seedvr2" -> seedvr2;
            case "image-layered" -> imageLayered;
            case "product-refinement" -> productRefinement;
            case "product-detail-image" -> productDetailImage;
            case "ecommerce-promotion-poster" -> ecommercePromotionPoster;
            case "character-setting-sheet" -> characterSettingSheet;
            case "emoji-sticker" -> emojiSticker;
            default -> throw new IllegalArgumentException("Unsupported image tool: " + toolId);
        };
    }

    @Data
    public static class Endpoint {
        private boolean enabled;
        private String endpoint;
        private String apiKey;
        private String model;
        private String mode = "edit";
        private String resultHostSuffixes = "";
        private Duration timeout = Duration.ofMinutes(10);
    }
}
