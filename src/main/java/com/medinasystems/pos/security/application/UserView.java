package com.medinasystems.pos.security.application;

import java.util.List;

import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.domain.Role;

/** A user as shown by the API. Never includes the password hash. */
public record UserView(Long id, String username, String fullName, String email, String externalCode,
                       boolean active, boolean mustChangePassword, List<String> roles) {

    public static UserView from(AppUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(),
                user.getExternalCode(), user.isActive(), user.isMustChangePassword(),
                user.getRoles().stream().map(Role::getCode).sorted().toList());
    }
}
