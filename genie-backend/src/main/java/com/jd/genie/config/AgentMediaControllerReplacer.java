package com.jd.genie.config;

import com.jd.genie.controller.CosAgentMediaController;
import com.jd.genie.service.agent.AgentImageStorage;
import com.jd.genie.service.agent.CosAgentImageStorage;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Uses the COS-aware controller only when agent.storage.provider=cos, keeping local development unchanged.
 */
@Component
public class AgentMediaControllerReplacer implements BeanPostProcessor {
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<AgentImageStorage> imageStorageProvider;

    public AgentMediaControllerReplacer(ObjectProvider<CosAgentImageStorage> cosStorageProvider,
                                        ObjectProvider<AgentImageStorage> imageStorageProvider) {
        this.cosStorageProvider = cosStorageProvider;
        this.imageStorageProvider = imageStorageProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!"agentMediaController".equals(beanName)) {
            return bean;
        }
        CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
        if (cosStorage == null) {
            return bean;
        }
        return new CosAgentMediaController(imageStorageProvider.getObject(), cosStorage);
    }
}
