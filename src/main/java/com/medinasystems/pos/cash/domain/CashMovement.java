package com.medinasystems.pos.cash.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * A deposit or a withdrawal of cash in a session. Created only through {@link CashSession#addMovement}, which checks
 * the session rules. The session is kept as an id, so the views never load it lazily.
 */
@Entity
@Table(name = "cash_movement")
public class CashMovement extends AuditableEntity {

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashMovementType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "authorized_by")
    private Long authorizedBy;

    protected CashMovement() {
    }

    CashMovement(Long cashSessionId, CashMovementType type, BigDecimal amount, String reason, Instant movedAt,
                 long userId, Long authorizedBy) {
        this.cashSessionId = Objects.requireNonNull(cashSessionId, "cashSessionId is required");
        this.type = Objects.requireNonNull(type, "type is required");
        this.amount = Objects.requireNonNull(amount, "amount is required");
        this.reason = Objects.requireNonNull(reason, "reason is required");
        this.movedAt = Objects.requireNonNull(movedAt, "movedAt is required");
        this.userId = userId;
        this.authorizedBy = authorizedBy;
    }

    public Long getCashSessionId() {
        return cashSessionId;
    }

    public CashMovementType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public Instant getMovedAt() {
        return movedAt;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAuthorizedBy() {
        return authorizedBy;
    }
}
