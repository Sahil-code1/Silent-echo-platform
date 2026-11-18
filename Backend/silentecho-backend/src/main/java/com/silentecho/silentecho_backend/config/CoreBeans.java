package com.silentecho.silentecho_backend.config;

import com.silentecho.silentecho_backend.evidence.EvidenceValidator;
import com.silentecho.silentecho_backend.security.CryptoService;
import com.silentecho.silentecho_backend.security.LoginRateLimiter;
import com.silentecho.silentecho_backend.security.SignInIdGenerator;
import com.silentecho.silentecho_backend.security.SignInIdHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class CoreBeans {

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SignInIdGenerator signInIdGenerator(SecureRandom secureRandom) {
        return new SignInIdGenerator(secureRandom);
    }

    @Bean
    public SignInIdHasher signInIdHasher(AppProperties properties) {
        return new SignInIdHasher(properties.hmacSecret());
    }

    @Bean
    public CryptoService cryptoService(AppProperties properties, SecureRandom secureRandom) {
        return CryptoService.fromBase64(properties.encryptionKey(), secureRandom);
    }

    @Bean
    public LoginRateLimiter loginRateLimiter(AppProperties properties, Clock clock) {
        return new LoginRateLimiter(properties.loginMaxFailures(), properties.loginWindow(),
                properties.loginLockout(), clock);
    }

    @Bean
    public EvidenceValidator evidenceValidator() {
        return new EvidenceValidator();
    }
}
