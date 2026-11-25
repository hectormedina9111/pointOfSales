package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * One spend of a credit note balance by a payment of a store. The redemption key is the payment's idempotency key, so
 * asking twice for the same payment spends once. Rows are never deleted: a released redemption stays, as RELEASED.
 * Lives only on the central node; stores never hold it.
 */
@Entity
@Table(name = "credit_note_redemption")
public class CreditNoteRedemption extends AuditableEntity {

    @Column(name = "credit_note_id", nullable = false)
    private Long creditNoteId;

    @Column(name = "redemption_key", nullable = false, unique = true, length = 100)
    private String redemptionKey;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "store_node_id", nullable = false)
    private Long storeNodeId;

    @Column(name = "sale_id")
    private Long saleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CreditNoteRedemptionStatus status;

    protected CreditNoteRedemption() {
    }

    /** A new redemption, applied: the balance of the note has already been reduced by the same amount. */
    public CreditNoteRedemption(long creditNoteId, String redemptionKey, BigDecimal amount, long storeNodeId) {
        this.creditNoteId = creditNoteId;
        this.redemptionKey = redemptionKey;
        this.amount = Money.scale2(amount);
        this.storeNodeId = storeNodeId;
        this.status = CreditNoteRedemptionStatus.APPLIED;
    }

    /** True when a retried request asks for the same note, amount and store as this redemption. */
    public boolean isSameRequest(long creditNoteId, BigDecimal requestedAmount, long storeNodeId) {
        return Objects.equals(this.creditNoteId, creditNoteId)
                && amount.compareTo(Money.scale2(requestedAmount)) == 0
                && Objects.equals(this.storeNodeId, storeNodeId);
    }

    /** APPLIED or CONFIRMED: the amount is taken from the balance. */
    public boolean isApplied() {
        return status != CreditNoteRedemptionStatus.RELEASED;
    }

    public boolean isConfirmed() {
        return status == CreditNoteRedemptionStatus.CONFIRMED;
    }

    public boolean isReleased() {
        return status == CreditNoteRedemptionStatus.RELEASED;
    }

    /** A released redemption asked again with its key: the amount is taken again. */
    public void reapply() {
        this.status = CreditNoteRedemptionStatus.APPLIED;
    }

    /** The sale that used this redemption reached the central node. */
    public void confirm(long saleId) {
        this.status = CreditNoteRedemptionStatus.CONFIRMED;
        this.saleId = saleId;
    }

    /** The payment failed: the amount goes back to the balance (the caller restores it). */
    public void release() {
        this.status = CreditNoteRedemptionStatus.RELEASED;
    }

    public Long getCreditNoteId() {
        return creditNoteId;
    }

    public String getRedemptionKey() {
        return redemptionKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getStoreNodeId() {
        return storeNodeId;
    }

    public Long getSaleId() {
        return saleId;
    }

    public CreditNoteRedemptionStatus getStatus() {
        return status;
    }
}
