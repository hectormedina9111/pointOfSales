package com.medinasystems.pos.security.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/** A person who signs in to the system. */
@Entity
@Table(name = "app_user")
public class AppUser extends AuditableEntity {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String fullName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    /** Counts a wrong password; locks the account when the limit is reached. */
    public void recordFailedLogin(Instant now) {
        if (lockedUntil != null && !isLocked(now)) {
            // The previous lock has expired: start counting again.
            failedAttempts = 0;
            lockedUntil = null;
        }
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Stores a new password hash; the user no longer has to change it. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
    }

    public void addRole(Role role) {
        roles.add(role);
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
