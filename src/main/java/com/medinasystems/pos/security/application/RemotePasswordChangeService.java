package com.medinasystems.pos.security.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.RemotePasswordChanges;
import com.medinasystems.pos.security.RemotePasswordResult;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RefreshTokenRepository;
import com.medinasystems.pos.shared.AuditEvent;

/**
 * Central node: applies a password changed in a store. Saving the user fires the sync trigger, so the new
 * password reaches every store by replication.
 */
@Service
@Transactional
public class RemotePasswordChangeService implements RemotePasswordChanges {

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public RemotePasswordChangeService(AppUserRepository users, RefreshTokenRepository refreshTokens, Clock clock,
                                       ApplicationEventPublisher events) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
        this.events = events;
    }

    @Override
    public RemotePasswordResult apply(long userId, String passwordHash, Instant changedAt, Long requiredBranchId) {
        Optional<AppUser> found = users.findById(userId);
        if (found.isEmpty()) {
            return RemotePasswordResult.USER_NOT_FOUND;
        }
        AppUser user = found.get();
        if (requiredBranchId != null && !user.hasBranch(requiredBranchId)) {
            return RemotePasswordResult.BRANCH_NOT_ALLOWED;
        }
        if (!user.applyRemotePasswordChange(passwordHash, changedAt)) {
            return RemotePasswordResult.STALE;
        }
        refreshTokens.revokeAllForUser(user, clock.instant());
        events.publishEvent(new AuditEvent("PASSWORD_CHANGED_AT_STORE", "app_user", userId, null, null));
        return RemotePasswordResult.APPLIED;
    }
}
