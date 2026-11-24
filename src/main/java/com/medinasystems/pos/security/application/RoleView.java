package com.medinasystems.pos.security.application;

import com.medinasystems.pos.security.domain.Role;

/** A role as shown by the API. */
public record RoleView(Long id, String code, String name, String description) {

    public static RoleView from(Role role) {
        return new RoleView(role.getId(), role.getCode(), role.getName(), role.getDescription());
    }
}
