package com.jd.genie.config;

import com.jd.genie.controller.CosAgentMediaController;
import com.jd.genie.controller.SignedCosAgentMediaController;
import com.jd.genie.service.agent.AgentAssetMetadataStore;
import com.jd.genie.service.agent.AgentHistoryStore;
import com.jd.genie.service.agent.AgentImageStorage;
import com.jd.genie.service.agent.CosAgentImageStorage;
import com.jd.genie.service.agent.CosSignedUrlService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/** Upgrades the COS controller with signed URLs and ownership metadata support. */
@Component
public class SignedCosAgentMediaControllerReplacer implements BeanPostProcessor, Ordered {
    private final ObjectProvider<AgentImageStorage> imageStorageProvider;
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;
    private final ObjectProvider<AgentHistoryStore> historyStoreProvider;
    private final ObjectProvider<AgentAssetMetadataStore> assetStoreProvider;

    public SignedCosAgentMediaControllerReplacer(ObjectProvider<AgentImageStorage> imageStorageProvider, ObjectProvider<CosAgentImageStorage> cosStorageProvider, ObjectProvider<CosSignedUrlService> signedUrlServiceProvider, ObjectProvider<AgentHistoryStore> historyStoreProvider, ObjectProvider<AgentAssetMetadataStore> assetStoreProvider) {
        this.imageStorageProvider = imageStorageProvider;
        this.cosStorageProvider = cosStorageProvider;
        this.signedUrlServiceProvider = signedUrlServiceProvider;
        this.historyStoreProvider = historyStoreProvider;
        this.assetStoreProvider = assetStoreProvider;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!"agentMediaController".equals(beanName) || !(bean instanceof CosAgentMediaController)) return bean;
        CosAgentImageStorage cosStorage = cosStorageProvider.getIfAvailable();
        CosSignedUrlService signedUrlService = signedUrlServiceProvider.getIfAvailable();
        AgentHistoryStore historyStore = historyStoreProvider.getIfAvailable();
        AgentAssetMetadataStore assetStore = assetStoreProvider.getIfAvailable();
        if (cosStorage == null || signedUrlService == null || historyStore == null || assetStore == null) return bean;
        return new SignedCosAgentMediaController(imageStorageProvider.getObject(), cosStorage, signedUrlService, historyStore, assetStore);
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
