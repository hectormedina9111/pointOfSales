package com.medinasystems.pos.security.web;

import java.util.List;

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
import com.medinasystems.pos.security.application.MenuItem;
import com.medinasystems.pos.security.application.PrivilegeService;
import com.medinasystems.pos.security.application.UserProfile;

/** Endpoints for the signed-in user. Available even when a password change is pending. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AuthService auth;
    private final PrivilegeService privilegeService;

    public MeController(AuthService auth, PrivilegeService privilegeService) {
        this.auth = auth;
        this.privilegeService = privilegeService;
    }

    @GetMapping
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return auth.profile(jwt.getSubject());
    }

    /**
     * Works on the central node and on a store. On a store the change is applied at once and queued for the
     * central node, which keeps the most recent password of each user.
     */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest request) {
        auth.changePassword(jwt.getSubject(), request.currentPassword(), request.newPassword());
    }

    /** The menu entries the user may see, as a tree. */
    @GetMapping("/menus")
    public List<MenuItem> menus(@AuthenticationPrincipal Jwt jwt) {
        return privilegeService.menusFor(userId(jwt));
    }

    /** Codes of the endpoint and action privileges the user holds. */
    @GetMapping("/privileges")
    public PrivilegeCodes privileges(@AuthenticationPrincipal Jwt jwt) {
        return new PrivilegeCodes(privilegeService.codesFor(userId(jwt)));
    }

    private Long userId(Jwt jwt) {
        return auth.profile(jwt.getSubject()).id();
    }

    /** BCrypt only uses the first 72 bytes, so longer passwords are rejected up front. */
    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(max = 72) String newPassword) {
    }

    public record PrivilegeCodes(List<String> codes) {
    }
}
