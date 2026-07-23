package com.jd.genie.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves development uploads locally. Production COS URLs do not use this handler.
 */
@Configuration
public class AgentUploadWebConfig implements WebMvcConfigurer {
    private final Path localDirectory;

    public AgentUploadWebConfig(@Value("${agent.storage.local-directory:./data/uploads/agent}") String localDirectory) {
        this.localDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
    }

    @Bean
    MultipartConfigElement agentMultipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(10));
        factory.setMaxRequestSize(DataSize.ofMegabytes(12));
        return factory.createMultipartConfig();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/agent/**").addResourceLocations(localDirectory.toUri().toString());
    }
}
