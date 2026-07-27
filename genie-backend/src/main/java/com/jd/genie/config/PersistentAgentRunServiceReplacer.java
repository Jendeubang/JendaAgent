package com.jd.genie.config;

import com.jd.genie.service.agent.AgentRunService;
import com.jd.genie.service.agent.ModelToolAgentRunService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Replaces the existing primary SSE bean without changing upstream source files.
 * The run service is resolved only when the original bean is initialized so this
 * BeanPostProcessor cannot force early creation of the persistence layer.
 */
@Component
public class PersistentAgentRunServiceReplacer implements BeanPostProcessor {
    private final ObjectProvider<ModelToolAgentRunService> modelToolAgentRunServiceProvider;

    public PersistentAgentRunServiceReplacer(ObjectProvider<ModelToolAgentRunService> modelToolAgentRunServiceProvider) {
        this.modelToolAgentRunServiceProvider = modelToolAgentRunServiceProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if ("persistentAgentRunService".equals(beanName)) {
            return (AgentRunService) (sessionId, request) -> modelToolAgentRunServiceProvider.getObject().startRun(sessionId, request);
        }
        return bean;
    }
}
