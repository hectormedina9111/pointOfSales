package com.medinasystems.pos.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AppUserTest {

    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    private static AppUser newUser() {
        return new AppUser("alice", "hash", "Alice");
    }

    private static void failTimes(AppUser user, int times, Instant at) {
        for (int i = 0; i < times; i++) {
            user.recordFailedLogin(at);
        }
    }

    @Test
    void locksAccountAfterFiveFailedLogins() {
        AppUser user = newUser();

        failTimes(user, 4, NOW);
        assertThat(user.isLocked(NOW)).isFalse();

        user.recordFailedLogin(NOW);
        assertThat(user.isLocked(NOW)).isTrue();
    }

    @Test
    void unlocksAfterFifteenMinutes() {
        AppUser user = newUser();
        failTimes(user, 5, NOW);

        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
    }

    @Test
    void successfulLoginResetsTheFailureCounter() {
        AppUser user = newUser();
        failTimes(user, 3, NOW);

        user.recordSuccessfulLogin(NOW);
        failTimes(user, 4, NOW);

        assertThat(user.isLocked(NOW)).isFalse();
    }

    @Test
    void expiredLockStartsCountingAgain() {
        AppUser user = newUser();
        failTimes(user, 5, NOW);

        Instant later = NOW.plus(Duration.ofMinutes(15));
        user.recordFailedLogin(later);

        assertThat(user.isLocked(later)).isFalse();
    }

    @Test
    void changingPasswordClearsTheMustChangeFlag() {
        AppUser user = newUser();
        assertThat(user.isMustChangePassword()).isTrue();

        user.changePassword("new-hash");

        assertThat(user.isMustChangePassword()).isFalse();
    }
}
