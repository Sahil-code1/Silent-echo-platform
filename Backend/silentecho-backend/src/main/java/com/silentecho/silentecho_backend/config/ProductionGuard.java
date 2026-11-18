package com.silentecho.silentecho_backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start under the {@code prod} profile unless the session cookie is marked Secure.
 * This stops a deployment from silently sending session cookies over plain HTTP.
 */
@Component
@Profile("prod")
public class ProductionGuard {

    private final Environment environment;

    public ProductionGuard(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        boolean secure = environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false);
        if (!secure) {
            throw new IllegalStateException(
                    "Production profile requires SILENTECHO_COOKIE_SECURE=true so session cookies are sent only over HTTPS");
        }
        boolean hmacSet = environment.getProperty("silentecho.hmac-secret", "").length() >= 32;
        if (!hmacSet) {
            throw new IllegalStateException("Production profile requires SILENTECHO_HMAC_SECRET (32+ characters)");
        }
    }
}
