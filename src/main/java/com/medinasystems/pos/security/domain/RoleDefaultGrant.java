package com.medinasystems.pos.security.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A default grant of a role that the startup seeder has already applied. It is a record, not a grant: once a
 * (role, privilege) pair is here, the seeder never grants it again, so an administrator who removes it keeps it
 * removed. Central node only; not replicated.
 */
@Entity
@Table(name = "role_default_grant")
@IdClass(RoleDefaultGrantKey.class)
public class RoleDefaultGrant {

    @Id
    @Column(name = "role_code", nullable = false, length = 30)
    private String roleCode;

    @Id
    @Column(name = "privilege_code", nullable = false, length = 100)
    private String privilegeCode;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    protected RoleDefaultGrant() {
    }

    public RoleDefaultGrant(String roleCode, String privilegeCode, Instant appliedAt) {
        this.roleCode = roleCode;
        this.privilegeCode = privilegeCode;
        this.appliedAt = appliedAt;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getPrivilegeCode() {
        return privilegeCode;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
