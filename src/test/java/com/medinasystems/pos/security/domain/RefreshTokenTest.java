package com.medinasystems.pos.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void isActiveUntilExpiry() {
        RefreshToken token = new RefreshToken(new AppUser("bob", "hash", "Bob"), "token-hash", NOW.plusSeconds(60));

        assertThat(token.isActive(NOW)).isTrue();
        assertThat(token.isActive(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void revokedTokenIsInactive() {
        RefreshToken token = new RefreshToken(new AppUser("bob", "hash", "Bob"), "token-hash", NOW.plusSeconds(60));

        token.revoke(NOW);

        assertThat(token.isActive(NOW)).isFalse();
    }
}
