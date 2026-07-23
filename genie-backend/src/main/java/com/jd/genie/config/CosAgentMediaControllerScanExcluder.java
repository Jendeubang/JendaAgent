package com.jd.genie.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * CosAgentMediaController is instantiated by AgentMediaControllerReplacer.
 * Remove its component-scanned definition so it cannot register a duplicate route.
 */
@Component
public class CosAgentMediaControllerScanExcluder implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition("cosAgentMediaController")) {
            registry.removeBeanDefinition("cosAgentMediaController");
        }
    }

    @Override
    public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        // No bean-factory changes are required.
    }
}
