package com.medinasystems.pos.security.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.ActionAuthorizer;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * Default {@link ActionAuthorizer}. The check runs in its own transaction so that a wrong password is kept in the
 * failed-attempts counter even though the call throws.
 */
@Service
public class DefaultActionAuthorizer implements ActionAuthorizer {

    private static final String DENIED = "AUTHORIZATION_DENIED";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PrivilegeService privileges;
    private final BranchScope scope;
    private final Clock clock;

    public DefaultActionAuthorizer(AppUserRepository users, PasswordEncoder passwordEncoder,
                                   PrivilegeService privileges, BranchScope scope, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.privileges = privileges;
        this.scope = scope;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessRuleException.class)
    public long authorize(String username, String password, String privilegeCode) {
        Instant now = clock.instant();
        AppUser user = users.findByUsername(username).orElseThrow(DefaultActionAuthorizer::denied);
        if (!user.isActive() || user.isLocked(now)) {
            throw denied();
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(now);
            throw denied();
        }
        if (!scope.allBranches() && user.getBranchIds().stream().noneMatch(scope::canSee)) {
            throw denied();
        }
        if (!privileges.codesFor(user.getId()).contains(privilegeCode)) {
            throw denied();
        }
        return user.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPrivilege(long userId, String privilegeCode) {
        return privileges.codesFor(userId).contains(privilegeCode);
    }

    private static BusinessRuleException denied() {
        return new BusinessRuleException(DENIED, "The authorization was not granted");
    }
}
