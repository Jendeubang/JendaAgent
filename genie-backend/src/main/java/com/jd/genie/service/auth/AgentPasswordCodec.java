package com.jd.genie.service.auth;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2 password hashing without adding a security framework dependency. */
@Component
public class AgentPasswordCodec {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encode(String password) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(salt) + "$" +
                Base64.getUrlEncoder().withoutPadding().encodeToString(derive(password, salt));
    }

    public boolean matches(String password, String encoded) {
        String[] values = encoded == null ? new String[0] : encoded.split("\\$", 2);
        if (values.length != 2) {
            return false;
        }
        byte[] salt = Base64.getUrlDecoder().decode(values[0]);
        byte[] expected = Base64.getUrlDecoder().decode(values[1]);
        return MessageDigest.isEqual(expected, derive(password, salt));
    }

    private byte[] derive(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to hash agent password", error);
        }
    }
}
