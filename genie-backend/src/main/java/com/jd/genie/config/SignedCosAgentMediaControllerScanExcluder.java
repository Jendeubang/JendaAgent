package com.jd.genie.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * SignedCosAgentMediaController is created by a BeanPostProcessor, not component scanning.
 */
@Component
public class SignedCosAgentMediaControllerScanExcluder implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition("signedCosAgentMediaController")) {
            registry.removeBeanDefinition("signedCosAgentMediaController");
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No bean-factory changes are required.
    }
}
