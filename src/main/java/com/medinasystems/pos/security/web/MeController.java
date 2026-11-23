package com.medinasystems.pos.security.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.security.application.AuthService;
import com.medinasystems.pos.security.application.UserProfile;

/** Endpoints for the signed-in user. Available even when a password change is pending. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AuthService auth;

    public MeController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return auth.profile(jwt.getSubject());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        auth.changePassword(jwt.getSubject(), request.currentPassword(), request.newPassword());
    }

    /** BCrypt only uses the first 72 bytes, so longer passwords are rejected up front. */
    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(max = 72) String newPassword) {
    }
}
