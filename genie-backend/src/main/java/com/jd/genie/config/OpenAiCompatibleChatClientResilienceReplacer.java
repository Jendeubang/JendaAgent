package com.jd.genie.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.service.agent.OpenAiCompatibleChatClient;
import com.jd.genie.service.agent.ResilientOpenAiCompatibleChatClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Compatibility replacement used while existing upstream source files remain read-only.
 * Providers avoid early bean creation while this BeanPostProcessor is registered.
 */
@Component
public class OpenAiCompatibleChatClientResilienceReplacer implements BeanPostProcessor {
    private final ObjectProvider<AgentRuntimeProperties> propertiesProvider;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;

    public OpenAiCompatibleChatClientResilienceReplacer(ObjectProvider<AgentRuntimeProperties> propertiesProvider,
                                                         ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.propertiesProvider = propertiesProvider;
        this.objectMapperProvider = objectMapperProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (beanName.equals("openAiCompatibleChatClient") && bean.getClass() == OpenAiCompatibleChatClient.class) {
            return new ResilientOpenAiCompatibleChatClient(propertiesProvider.getObject(), objectMapperProvider.getObject());
        }
        return bean;
    }
}
