package com.medinasystems.pos.security.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RefreshTokenRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** User administration: create, edit, list, and reset passwords. */
@Service
@Transactional
public class UserService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public UserService(AppUserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, Clock clock, ApplicationEventPublisher events) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<UserView> list(Pageable pageable) {
        return users.findAll(pageable).map(UserView::from);
    }

    @Transactional(readOnly = true)
    public UserView get(Long id) {
        return UserView.from(find(id));
    }

    /** The new user must change the initial password at the first sign-in. */
    public UserView create(String username, String fullName, String email, String externalCode,
                           Collection<String> roleCodes, String initialPassword) {
        if (users.existsByUsername(username)) {
            throw new BusinessRuleException("user.username.duplicated", "The username is already in use");
        }
        String mail = blankToNull(email);
        String externalId = blankToNull(externalCode);
        checkUnique(mail, externalId, null);

        AppUser user = new AppUser(username, passwordEncoder.encode(initialPassword), fullName, clock.instant());
        user.update(fullName, mail, externalId, true, rolesFor(roleCodes));
        return UserView.from(users.save(user));
    }

    /** The username and password are not changed here. */
    public UserView update(Long id, String fullName, String email, String externalCode, boolean active,
                           Collection<String> roleCodes) {
        AppUser user = find(id);
        String mail = blankToNull(email);
        String externalId = blankToNull(externalCode);
        checkUnique(mail, externalId, id);
        user.update(fullName, mail, externalId, active, rolesFor(roleCodes));
        return UserView.from(user);
    }

    @Transactional(readOnly = true)
    public List<Long> branchIds(Long id) {
        return find(id).getBranchIds().stream().sorted().toList();
    }

    /**
     * Replaces the branches of the user. Unknown branch ids are caught by the foreign key
     * on user_branch when the change is flushed, and reported as BRANCH_NOT_FOUND.
     */
    public List<Long> replaceBranches(Long id, Collection<Long> branchIds) {
        AppUser user = find(id);
        List<Long> previous = user.getBranchIds().stream().sorted().toList();
        user.assignBranches(Set.copyOf(branchIds));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("BRANCH_NOT_FOUND", "One or more branch ids do not exist");
        }
        List<Long> current = user.getBranchIds().stream().sorted().toList();
        events.publishEvent(AuditEvent.updated("USER_BRANCHES_CHANGED", "app_user", id,
                Map.of("branchIds", previous), Map.of("branchIds", current)));
        return current;
    }

    /** Sets a temporary password and signs the user out everywhere (refresh tokens are revoked). */
    public void resetPassword(Long id, String newPassword) {
        AppUser user = find(id);
        Instant now = clock.instant();
        user.resetPassword(passwordEncoder.encode(newPassword), now);
        refreshTokens.revokeAllForUser(user, now);
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("user", id));
    }

    /** Checks email and external code. Pass the id of the user being edited, or null when creating. */
    private void checkUnique(String email, String externalCode, Long ownId) {
        if (email != null && (ownId == null ? users.existsByEmail(email) : users.existsByEmailAndIdNot(email, ownId))) {
            throw new BusinessRuleException("user.email.duplicated", "The email is already in use");
        }
        if (externalCode != null && (ownId == null
                ? users.existsByExternalCode(externalCode)
                : users.existsByExternalCodeAndIdNot(externalCode, ownId))) {
            throw new BusinessRuleException("user.external_code.duplicated", "The external code is already in use");
        }
    }

    private Set<Role> rolesFor(Collection<String> codes) {
        Set<String> wanted = Set.copyOf(codes);
        List<Role> found = roles.findByCodeIn(wanted);
        if (found.size() != wanted.size()) {
            throw new BusinessRuleException("role.unknown", "One or more role codes do not exist");
        }
        return new HashSet<>(found);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
