package com.medinasystems.pos.cash.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * A cash register (punto de emisión) of a branch. The branch is kept as an id: the cash module never holds
 * organization entities. The receipt counter is not here: it is store-local and lives in its own table.
 */
@Entity
@Table(name = "cash_register")
public class CashRegister extends AuditableEntity {

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "receipt_prefix", nullable = false, length = 20)
    private String receiptPrefix;

    @Column(nullable = false)
    private boolean active = true;

    protected CashRegister() {
    }

    public CashRegister(Long branchId, String code, String name, String receiptPrefix, boolean active) {
        apply(branchId, code, name, receiptPrefix, active);
    }

    /** Changes every field. The code must stay unique in its branch; the service checks that before calling. */
    public void update(Long branchId, String code, String name, String receiptPrefix, boolean active) {
        apply(branchId, code, name, receiptPrefix, active);
    }

    private void apply(Long branchId, String code, String name, String receiptPrefix, boolean active) {
        Objects.requireNonNull(branchId, "branchId is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(receiptPrefix, "receipt prefix is required");
        this.branchId = branchId;
        this.code = code;
        this.name = name;
        this.receiptPrefix = receiptPrefix;
        this.active = active;
    }

    public Long getBranchId() {
        return branchId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getReceiptPrefix() {
        return receiptPrefix;
    }

    public boolean isActive() {
        return active;
    }
}
