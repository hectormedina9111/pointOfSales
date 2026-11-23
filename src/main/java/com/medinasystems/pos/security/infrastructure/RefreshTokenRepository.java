package com.medinasystems.pos.security.infrastructure;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every active refresh token of a user, for example after a password change. */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user = :user and t.revokedAt is null")
    int revokeAllForUser(@Param("user") AppUser user, @Param("now") Instant now);
}
