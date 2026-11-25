package com.medinasystems.pos.security.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link RoleDefaultGrant}: the role code and the privilege code. */
public class RoleDefaultGrantKey implements Serializable {

    private String roleCode;
    private String privilegeCode;

    public RoleDefaultGrantKey() {
    }

    public RoleDefaultGrantKey(String roleCode, String privilegeCode) {
        this.roleCode = roleCode;
        this.privilegeCode = privilegeCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoleDefaultGrantKey key)) {
            return false;
        }
        return Objects.equals(roleCode, key.roleCode) && Objects.equals(privilegeCode, key.privilegeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleCode, privilegeCode);
    }
}
