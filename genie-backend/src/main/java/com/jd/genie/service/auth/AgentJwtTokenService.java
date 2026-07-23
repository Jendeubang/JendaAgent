package com.jd.genie.service.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.AgentAuthProperties;
import com.jd.genie.model.auth.AgentPrincipal;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Issues short-lived, revocable HS256 access JWTs. */
@Service
public class AgentJwtTokenService {
    private final ObjectMapper objectMapper;
    private final AgentAuthProperties properties;
    private final AgentTokenStore tokenStore;

    public AgentJwtTokenService(ObjectMapper objectMapper, AgentAuthProperties properties, AgentTokenStore tokenStore) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tokenStore = tokenStore;
    }

    public String issue(AgentPrincipal principal) {
        requireSecret();
        long now = Instant.now().getEpochSecond();
        long expiresAt = now + properties.getTokenTtl().toSeconds();
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", properties.getIssuer());
            claims.put("sub", principal.userId());
            claims.put("username", principal.username());
            claims.put("typ", "access");
            claims.put("jti", UUID.randomUUID().toString());
            claims.put("iat", now);
            claims.put("exp", expiresAt);
            String payload = encodeJson(claims);
            String unsigned = header + "." + payload;
            return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(unsigned));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to issue agent access token", error);
        }
    }

    public AgentPrincipal verify(String token) {
        return parse(token, true).principal();
    }

    public void revoke(String token) {
        try {
            VerifiedAccessToken parsed = parse(token, false);
            if (parsed.expiresAt().isAfter(Instant.now())) tokenStore.revokeAccess(parsed.tokenId(), parsed.expiresAt());
        } catch (RuntimeException ignored) {
            // Logout is idempotent. Invalid or expired access tokens need no server action.
        }
    }

    public long expiresInSeconds() {
        return properties.getTokenTtl().toSeconds();
    }

    private VerifiedAccessToken parse(String token, boolean checkRevocation) {
        requireSecret();
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", 3);
            if (parts.length != 3) throw new IllegalArgumentException("Malformed bearer token");
            byte[] expected = hmac(parts[0] + "." + parts[1]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) throw new IllegalArgumentException("Invalid bearer token signature");
            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() { });
            if (!properties.getIssuer().equals(claims.get("iss")) || !"access".equals(claims.get("typ"))) throw new IllegalArgumentException("Unexpected bearer token");
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (expiresAt <= Instant.now().getEpochSecond()) throw new IllegalArgumentException("Bearer token expired");
            String userId = String.valueOf(claims.get("sub"));
            String username = String.valueOf(claims.get("username"));
            String tokenId = String.valueOf(claims.get("jti"));
            if (userId.isBlank() || username.isBlank() || tokenId.isBlank()) throw new IllegalArgumentException("Bearer token subject missing");
            if (checkRevocation && tokenStore.isAccessRevoked(tokenId)) throw new IllegalArgumentException("Bearer token was revoked");
            return new VerifiedAccessToken(new AgentPrincipal(userId, username), tokenId, Instant.ofEpochSecond(expiresAt));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid bearer token", error);
        }
    }

    private String encodeJson(Map<String, ?> value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private void requireSecret() {
        if (properties.getJwtSecret() == null || properties.getJwtSecret().length() < 32) throw new IllegalStateException("agent.auth.jwt-secret must contain at least 32 characters when JWT auth is enabled");
    }

    private record VerifiedAccessToken(AgentPrincipal principal, String tokenId, Instant expiresAt) {
    }
}