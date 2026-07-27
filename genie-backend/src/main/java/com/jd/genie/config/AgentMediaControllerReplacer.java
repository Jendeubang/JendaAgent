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
import org.springframework.stereotype.Component;

/**
 * Replaces the base upload controller with the COS adapter. The signed adapter
 * also persists ownership metadata so fallback uploads appear in workspace/history.
 */
@Component
public class AgentMediaControllerReplacer implements BeanPostProcessor {
    private final ObjectProvider<CosAgentImageStorage> cosStorageProvider;
    private final ObjectProvider<AgentImageStorage> imageStorageProvider;
    private final ObjectProvider<CosSignedUrlService> signedUrlServiceProvider;
    private final ObjectProvider<AgentHistoryStore> historyStoreProvider;
    private final ObjectProvider<AgentAssetMetadataStore> assetStoreProvider;

    public AgentMediaControllerReplacer(ObjectProvider<CosAgentImageStorage> cosStorageProvider,
                                        ObjectProvider<AgentImageStorage> imageStorageProvider,
                                        ObjectProvider<CosSignedUrlService> signedUrlServiceProvider,
                                        ObjectProvider<AgentHistoryStore> historyStoreProvider,
                                        ObjectProvider<AgentAssetMetadataStore> assetStoreProvider) {
        this.cosStorageProvider = cosStorageProvider;
        this.imageStorageProvider = imageStorageProvider;
        this.signedUrlServiceProvider = signedUrlServiceProvider;
        this.historyStoreProvider = historyStoreProvider;
        this.assetStoreProvider = assetStoreProvider;
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
        CosSignedUrlService signedUrlService = signedUrlServiceProvider.getIfAvailable();
        AgentHistoryStore historyStore = historyStoreProvider.getIfAvailable();
        AgentAssetMetadataStore assetStore = assetStoreProvider.getIfAvailable();
        if (signedUrlService != null && historyStore != null && assetStore != null) {
            return new SignedCosAgentMediaController(imageStorageProvider.getObject(), cosStorage, signedUrlService, historyStore, assetStore);
        }
        return new CosAgentMediaController(imageStorageProvider.getObject(), cosStorage);
    }
}