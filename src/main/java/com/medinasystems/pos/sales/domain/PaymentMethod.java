package com.medinasystems.pos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * A way to pay a sale. The code identifies the method in the code and is fixed; the name and the two flags
 * can be changed by the central node.
 */
@Entity
@Table(name = "payment_method")
public class PaymentMethod extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** True when the payment needs a reference, such as the authorization of the card terminal. */
    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference;

    /** True when the payment can be higher than the sale and return change. Only cash does. */
    @Column(name = "allows_change", nullable = false)
    private boolean allowsChange;

    @Column(nullable = false)
    private boolean active = true;

    protected PaymentMethod() {
    }

    public PaymentMethod(String code, String name, boolean requiresReference, boolean allowsChange, boolean active) {
        this.code = code;
        this.name = name;
        this.requiresReference = requiresReference;
        this.allowsChange = allowsChange;
        this.active = active;
    }

    /** Changes the name and the flags. The code never changes. */
    public void update(String name, boolean requiresReference, boolean allowsChange, boolean active) {
        this.name = name;
        this.requiresReference = requiresReference;
        this.allowsChange = allowsChange;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isRequiresReference() {
        return requiresReference;
    }

    public boolean isAllowsChange() {
        return allowsChange;
    }

    public boolean isActive() {
        return active;
    }
}
