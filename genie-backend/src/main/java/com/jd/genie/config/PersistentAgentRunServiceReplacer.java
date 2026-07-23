package com.jd.genie.config;

import com.jd.genie.service.agent.AgentRunService;
import com.jd.genie.service.agent.ModelToolAgentRunService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Replaces the existing primary SSE bean without changing upstream source files.
 * The original bean definition remains primary, so existing controller injection keeps working.
 */
@Component
public class PersistentAgentRunServiceReplacer implements BeanPostProcessor {
    private final ModelToolAgentRunService modelToolAgentRunService;

    public PersistentAgentRunServiceReplacer(ModelToolAgentRunService modelToolAgentRunService) {
        this.modelToolAgentRunService = modelToolAgentRunService;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("persistentAgentRunService".equals(beanName)) {
            return (AgentRunService) modelToolAgentRunService::startRun;
        }
        return bean;
    }
}
