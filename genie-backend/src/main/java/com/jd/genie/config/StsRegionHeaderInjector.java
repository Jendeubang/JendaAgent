package com.jd.genie.config;

import com.jd.genie.service.agent.StsDirectUploadTicketService;
import okhttp3.OkHttpClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * The STS API requires X-TC-Region. Inject it only into the STS service HTTP client.
 * This is a temporary bridge until the source file can be directly edited.
 */
@Component
public class StsRegionHeaderInjector implements BeanPostProcessor {
    private final String region;

    public StsRegionHeaderInjector(Environment environment) {
        this.region = environment.getProperty("agent.storage.cos.region", "ap-shanghai");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof StsDirectUploadTicketService)) {
            return bean;
        }
        try {
            Field clientField = StsDirectUploadTicketService.class.getDeclaredField("client");
            clientField.setAccessible(true);
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        if ("sts.tencentcloudapi.com".equalsIgnoreCase(chain.request().url().host())) {
                            return chain.proceed(chain.request().newBuilder().header("X-TC-Region", region).build());
                        }
                        return chain.proceed(chain.request());
                    })
                    .build();
            clientField.set(bean, client);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to configure STS region header", error);
        }
        return bean;
    }
}
