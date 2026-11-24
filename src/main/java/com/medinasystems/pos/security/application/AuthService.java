package com.medinasystems.pos.security.application;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.LoginPolicy;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.domain.RefreshToken;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RefreshTokenRepository;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Login, token refresh and logout, plus the signed-in user's own password change. */
@Service
@Transactional
public class AuthService {

    static final int MIN_PASSWORD_LENGTH = 10;
    private static final String INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenService tokens;
    private final PasswordEncoder passwordEncoder;
    private final LoginPolicy loginPolicy;
    private final Clock clock;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, TokenService tokens,
                       PasswordEncoder passwordEncoder, LoginPolicy loginPolicy, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.loginPolicy = loginPolicy;
        this.clock = clock;
    }

    /**
     * Failed attempts are saved even though the call throws: the transaction must commit,
     * otherwise the lockout counter would never grow.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public TokenPair login(String username, String password) {
        Instant now = clock.instant();
        AppUser user = users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (user.isLocked(now)) {
            throw new AccountLockedException();
        }
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(now);
            throw new InvalidCredentialsException();
        }
        // Checked after the password, so a wrong password never reveals the node's rules.
        loginPolicy.checkCanLogin(user.getId(), user.getBranchIds());
        user.recordSuccessfulLogin(now);
        return issueTokens(user, now);
    }

    /** Rotates the refresh token: the presented one is revoked and a new pair is issued. */
    public TokenPair refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN));
        if (!stored.isActive(now) || !stored.getUser().isActive()) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN);
        }
        loginPolicy.checkCanLogin(stored.getUser().getId(), stored.getUser().getBranchIds());
        stored.revoke(now);
        return issueTokens(stored.getUser(), now);
    }

    /** Revokes the refresh token. An unknown token is ignored. */
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(TokenService.hash(refreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        AppUser user = users.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessRuleException("password.too_short", "The password must have at least 10 characters");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleException("password.same_as_current",
                    "The new password must be different from the current one");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user, clock.instant());
    }

    @Transactional(readOnly = true)
    public UserProfile profile(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("user", username));
        return new UserProfile(user.getId(), user.getUsername(), user.getFullName(),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                user.isMustChangePassword());
    }

    private TokenPair issueTokens(AppUser user, Instant now) {
        String refreshToken = tokens.newRefreshToken();
        refreshTokens.save(new RefreshToken(user, TokenService.hash(refreshToken), now.plus(tokens.refreshTokenTtl())));
        return new TokenPair(tokens.createAccessToken(user, now), refreshToken,
                tokens.accessTokenTtlSeconds(), user.isMustChangePassword());
    }
}
