package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * A payment of a sale. {@code amount} is what it applies to the sale; for cash it is net of change, and the
 * amount received is kept beside it. The card number is never stored, only its last four digits.
 */
@Entity
@Table(name = "payment")
public class Payment extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_received", precision = 14, scale = 2)
    private BigDecimal amountReceived;

    @Column(length = 60)
    private String reference;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    /** Credit note paid with, for the method CREDIT_NOTE. No foreign key: see V19. */
    @Column(name = "credit_note_id")
    private Long creditNoteId;

    /** Idempotency key of the redemption that took the amount from the note. */
    @Column(name = "redemption_key", length = 100)
    private String redemptionKey;

    protected Payment() {
    }

    Payment(Sale sale, PaymentDraft draft, Instant paidAt) {
        this.sale = sale;
        this.paymentMethod = draft.method();
        this.amount = draft.amount();
        this.amountReceived = draft.amountReceived();
        this.reference = draft.reference();
        this.cardLast4 = draft.cardLast4();
        this.paidAt = paidAt;
        this.creditNoteId = draft.creditNoteId();
        this.redemptionKey = draft.redemptionKey();
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getAmountReceived() {
        return amountReceived;
    }

    public String getReference() {
        return reference;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Long getCreditNoteId() {
        return creditNoteId;
    }

    public String getRedemptionKey() {
        return redemptionKey;
    }
}
