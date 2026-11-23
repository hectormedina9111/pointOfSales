package com.medinasystems.pos.security.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings bound from {@code pos.security.*}.
 *
 * @param jwt                  signing key and token lifetimes
 * @param adminInitialPassword password for the first admin; blank means generate one at startup
 */
@ConfigurationProperties("pos.security")
public record SecurityProperties(Jwt jwt, String adminInitialPassword) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

        private static final int MIN_SECRET_BYTES = 32;

        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException("pos.security.jwt.secret must be at least 32 bytes");
            }
        }

        /** HMAC key used to sign and verify access tokens. */
        public SecretKey secretKey() {
            return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        }
    }
}
