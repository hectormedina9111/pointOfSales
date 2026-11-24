package com.medinasystems.pos.catalog.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/** A unit in which products are sold, such as UNIT, KG or LT. */
@Entity
@Table(name = "unit_of_measure")
public class UnitOfMeasure extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "allows_decimals", nullable = false)
    private boolean allowsDecimals;

    @Column(nullable = false)
    private boolean active = true;

    protected UnitOfMeasure() {
    }

    public UnitOfMeasure(String code, String name, boolean allowsDecimals, boolean active) {
        update(code, name, allowsDecimals, active);
    }

    public void update(String code, String name, boolean allowsDecimals, boolean active) {
        this.code = Objects.requireNonNull(code, "code is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.allowsDecimals = allowsDecimals;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isAllowsDecimals() {
        return allowsDecimals;
    }

    public boolean isActive() {
        return active;
    }
}
