package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A credit note: a balance owed to the customer, issued by a return that was not made on the day of the sale. It is a
 * document, so it is never deleted. The balance is spent in later purchases (a later step).
 */
@Entity
@Table(name = "credit_note")
public class CreditNote extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "return_sale_id", nullable = false)
    private Long returnSaleId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CreditNoteStatus status = CreditNoteStatus.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected CreditNote() {
    }

    /** Issues a note for the whole amount of a return. The balance starts equal to the amount. */
    public CreditNote(String number, long customerId, long returnSaleId, long branchId, BigDecimal amount,
                      Instant issuedAt) {
        this.number = number;
        this.customerId = customerId;
        this.returnSaleId = returnSaleId;
        this.branchId = branchId;
        this.amount = Money.scale2(amount);
        this.balance = this.amount;
        this.issuedAt = issuedAt;
    }

    /**
     * Takes an amount from the balance for a payment. A note that is used up, or whose balance is less than the amount,
     * cannot pay it. The note becomes USED when its balance reaches zero.
     *
     * @throws BusinessRuleException CREDIT_NOTE_INSUFFICIENT_BALANCE
     */
    public void spend(BigDecimal value) {
        BigDecimal amountToSpend = Money.scale2(value);
        if (status == CreditNoteStatus.USED || balance.compareTo(amountToSpend) < 0) {
            throw new BusinessRuleException("CREDIT_NOTE_INSUFFICIENT_BALANCE",
                    "The credit note does not have enough balance");
        }
        this.balance = Money.scale2(balance.subtract(amountToSpend));
        if (balance.signum() == 0) {
            this.status = CreditNoteStatus.USED;
        }
    }

    /** Gives back an amount that a failed payment had taken. The note is active again. */
    public void restore(BigDecimal value) {
        this.balance = Money.scale2(balance.add(Money.scale2(value)));
        this.status = CreditNoteStatus.ACTIVE;
    }

    public String getNumber() {
        return number;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Long getReturnSaleId() {
        return returnSaleId;
    }

    public Long getBranchId() {
        return branchId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public CreditNoteStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
