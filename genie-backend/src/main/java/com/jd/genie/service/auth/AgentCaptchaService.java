package com.jd.genie.service.auth;

import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentCaptchaResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Single-use CAPTCHA backed by Redis with no plaintext answer persisted. */
@Service
public class AgentCaptchaService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final AgentRedisSupport redis;
    private final AgentPasswordCodec passwordCodec;
    private final AgentAuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public AgentCaptchaService(AgentRedisSupport redis, AgentPasswordCodec passwordCodec, AgentAuthProperties properties) {
        this.redis = redis;
        this.passwordCodec = passwordCodec;
        this.properties = properties;
    }

    public AgentCaptchaResponse create() {
        String answer = randomAnswer();
        String id = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(properties.getCaptchaTtl());
        if (!redis.set("captcha:" + id, passwordCodec.encode(answer), properties.getCaptchaTtl())) {
            throw new IllegalStateException("CAPTCHA service is temporarily unavailable");
        }
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='168' height='54' viewBox='0 0 168 54'><rect width='168' height='54' rx='8' fill='#eef5ff'/><path d='M8 14L160 40M10 42L156 12' stroke='#9ab9e7' stroke-width='2'/><text x='24' y='37' font-family='monospace' font-size='29' font-weight='700' letter-spacing='7' fill='#1d4ed8'>" + answer + "</text></svg>";
        String dataUrl = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new AgentCaptchaResponse(id, dataUrl, expiresAt);
    }

    public void verify(String captchaId, String answer) {
        if (!properties.isCaptchaEnabled()) return;
        if (captchaId == null || captchaId.isBlank() || answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("CAPTCHA is required");
        }
        String encoded = redis.getAndDelete("captcha:" + captchaId).orElseThrow(() -> new IllegalArgumentException("CAPTCHA is invalid or expired"));
        if (!passwordCodec.matches(answer.trim().toUpperCase(), encoded)) {
            throw new IllegalArgumentException("CAPTCHA is invalid or expired");
        }
    }

    private String randomAnswer() {
        StringBuilder result = new StringBuilder(5);
        for (int index = 0; index < 5; index++) result.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return result.toString();
    }
}