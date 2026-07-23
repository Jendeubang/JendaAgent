package com.jd.genie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.service.agent.OpenAiCompatibleChatClient;
import com.jd.genie.service.agent.ResilientOpenAiCompatibleChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Compatibility replacement used while existing upstream source files remain read-only.
 */
@Component
public class OpenAiCompatibleChatClientResilienceReplacer implements BeanPostProcessor {
    private final AgentRuntimeProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleChatClientResilienceReplacer(AgentRuntimeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (beanName.equals("openAiCompatibleChatClient") && bean.getClass() == OpenAiCompatibleChatClient.class) {
            return new ResilientOpenAiCompatibleChatClient(properties, objectMapper);
        }
        return bean;
    }
}
