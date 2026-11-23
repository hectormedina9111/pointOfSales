package com.medinasystems.pos.security.application;

import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.PrivilegeType;

/** A privilege as shown by the API. */
public record PrivilegeView(Long id, String code, PrivilegeType type, String name, String description,
                            String module, String httpMethod, String pathPattern, String parentCode,
                            String frontendRoute, String icon, int sortOrder, boolean active) {

    public static PrivilegeView from(Privilege privilege) {
        return new PrivilegeView(privilege.getId(), privilege.getCode(), privilege.getType(), privilege.getName(),
                privilege.getDescription(), privilege.getModule(), privilege.getHttpMethod(),
                privilege.getPathPattern(),
                privilege.getParent() == null ? null : privilege.getParent().getCode(),
                privilege.getFrontendRoute(), privilege.getIcon(), privilege.getSortOrder(), privilege.isActive());
    }
}
