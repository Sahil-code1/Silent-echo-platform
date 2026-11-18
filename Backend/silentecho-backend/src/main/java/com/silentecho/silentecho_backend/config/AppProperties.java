package com.silentecho.silentecho_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * SilentEcho settings. Secrets have no defaults: the application fails at startup if they are
 * missing or too weak, rather than running with a guessable key.
 */
@ConfigurationProperties(prefix = "silentecho")
public record AppProperties(
        String hmacSecret,
        String encryptionKey,
        @DefaultValue("./data/evidence") String evidenceDir,
        @DefaultValue({"http://127.0.0.1:5500", "http://localhost:5500"}) List<String> allowedOrigins,
        @DefaultValue("5") int loginMaxFailures,
        @DefaultValue("15m") Duration loginWindow,
        @DefaultValue("15m") Duration loginLockout
) {
    public AppProperties {
        if (hmacSecret == null || hmacSecret.length() < 32) {
            throw new IllegalStateException(
                    "SILENTECHO_HMAC_SECRET must be set and at least 32 characters long");
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "SILENTECHO_ENCRYPTION_KEY must be set (base64 of 32 random bytes)");
        }
        try {
            if (Base64.getDecoder().decode(encryptionKey.trim()).length != 32) {
                throw new IllegalStateException("SILENTECHO_ENCRYPTION_KEY must decode to 32 bytes");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SILENTECHO_ENCRYPTION_KEY is not valid Base64", e);
        }
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
