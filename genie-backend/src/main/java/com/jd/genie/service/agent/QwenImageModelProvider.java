package com.jd.genie.service.agent;

import com.jd.genie.model.agent.AgentToolGatewayRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Keeps the existing Qwen adapters behind the shared provider contract. */
@Component
@RequiredArgsConstructor
public class QwenImageModelProvider implements ImageModelProvider {
    private final QwenImageGenerateGatewayClient generateClient;
    private final QwenImageEditGatewayClient editClient;

    @Override
    public String id() {
        return "qwen";
    }

    @Override
    public ImageModelResult generate(AgentToolGatewayRequest request) {
        QwenImageGenerateGatewayClient.GeneratedImage result = generateClient.generate(request);
        return new ImageModelResult(result.imageUrl(), result.text(), id(), false);
    }

    @Override
    public ImageModelResult edit(AgentToolGatewayRequest request) {
        QwenImageEditGatewayClient.EditedImage result = editClient.edit(request);
        return new ImageModelResult(result.imageUrl(), result.text(), id(), false);
    }
}