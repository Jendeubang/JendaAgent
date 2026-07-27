package com.jd.genie.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Product authentication, session and Tencent SMS configuration. */
@Data
@Component
@ConfigurationProperties(prefix = "agent.auth")
public class AgentAuthProperties {
    private boolean enabled;
    private String jwtSecret;
    private String issuer = "jenda-agent";
    private Duration tokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    /** Refresh credentials are transport-only: never expose them to browser JavaScript. */
    private String refreshCookieName = "jenda_refresh";
    private String refreshCookiePath = "/api/v1/auth";
    private boolean refreshCookieSecure = false;
    private String refreshCookieSameSite = "Lax";
    private boolean exposeRefreshTokenInBody;
    private boolean captchaEnabled;
    private Duration captchaTtl = Duration.ofMinutes(5);
    private List<String> adminUsernames = new ArrayList<>();
    private int loginMaxFailures = 5;
    private Duration loginFailureWindow = Duration.ofMinutes(15);
    private Duration smsCodeTtl = Duration.ofMinutes(5);
    private boolean smsEnabled;
    private boolean smsDevelopmentMode = true;
    private String smsSecretId;
    private String smsSecretKey;
    private String smsSdkAppId;
    private String smsSignName;
    private String smsTemplateId;
    private String smsRegion = "ap-guangzhou";
}