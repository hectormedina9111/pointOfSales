package com.medinasystems.pos.cash.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * The shift of one cashier at one cash register. Opened with a cash float, it takes deposits and withdrawals, and is
 * closed with a count per payment method. A document of the store: it is never deleted.
 */
@Entity
@Table(name = "cash_session")
public class CashSession extends AuditableEntity {

    /** Payment method code of cash. The sales module seeds it (V12); cash is the only method that gives change. */
    public static final String CASH_METHOD = "CASH";

    @Column(name = "cash_register_id", nullable = false)
    private Long cashRegisterId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashSessionStatus status;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opening_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingAmount;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "expected_total", precision = 14, scale = 2)
    private BigDecimal expectedTotal;

    @Column(name = "counted_total", precision = 14, scale = 2)
    private BigDecimal countedTotal;

    @Column(precision = 14, scale = 2)
    private BigDecimal difference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "count_detail")
    private List<CountLine> countDetail;

    @Column(length = 500)
    private String notes;

    protected CashSession() {
    }

    private CashSession(long cashRegisterId, long userId, BigDecimal openingAmount, Instant openedAt) {
        this.cashRegisterId = cashRegisterId;
        this.userId = userId;
        this.status = CashSessionStatus.OPEN;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt is required");
        this.openingAmount = openingAmount;
    }

    /** Opens a session with the cash float. The float is rounded to cents and cannot be negative. */
    public static CashSession open(long cashRegisterId, long userId, BigDecimal openingAmount, Instant openedAt) {
        BigDecimal fund = money(openingAmount);
        if (fund.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "The opening amount cannot be negative");
        }
        return new CashSession(cashRegisterId, userId, fund, openedAt);
    }

    /** Only the owner can change an open session. Checked before anything else, so a stranger learns nothing. */
    public void requireOpenBy(long actorUserId) {
        if (!Objects.equals(userId, actorUserId)) {
            throw new BusinessRuleException("SESSION_NOT_OWNED", "The cash session belongs to another cashier");
        }
        if (status != CashSessionStatus.OPEN) {
            throw new BusinessRuleException("SESSION_NOT_OPEN", "The cash session is closed");
        }
    }

    /**
     * Records a deposit or a withdrawal. The amount is rounded to cents and must be greater than zero.
     *
     * @param authorizedBy user who authorized a withdrawal (the cashier when they hold the privilege); null for deposits
     */
    public CashMovement addMovement(long actorUserId, CashMovementType type, BigDecimal amount, String reason,
                                    Long authorizedBy, Instant movedAt) {
        requireOpenBy(actorUserId);
        Objects.requireNonNull(type, "type is required");
        BigDecimal value = money(amount);
        if (value.signum() <= 0) {
            throw new BusinessRuleException("INVALID_MOVEMENT_AMOUNT", "The amount must be greater than zero");
        }
        return new CashMovement(getId(), type, value, reason, movedAt, actorUserId, authorizedBy);
    }

    /**
     * Expected amount per payment method. CASH is the opening float, plus deposits, minus withdrawals, plus the cash
     * collected net of change. Other methods are what was collected. CASH is always present.
     */
    public Map<String, BigDecimal> expectedByMethod(List<CashMovement> movements,
                                                    Map<String, BigDecimal> collectedByMethod) {
        BigDecimal cash = openingAmount;
        for (CashMovement movement : movements) {
            cash = movement.getType() == CashMovementType.DEPOSIT
                    ? cash.add(movement.getAmount())
                    : cash.subtract(movement.getAmount());
        }
        cash = cash.add(collectedByMethod.getOrDefault(CASH_METHOD, BigDecimal.ZERO));

        Map<String, BigDecimal> expected = new TreeMap<>();
        collectedByMethod.forEach((method, amount) -> expected.put(method, money(amount)));
        expected.put(CASH_METHOD, money(cash));
        return expected;
    }

    /**
     * Closes the session with the count. Every method that has an expected amount or a count is compared; a method
     * that was not counted counts as zero. The totals and the detail are stored with the closing.
     *
     * @param expected expected amount per method, from {@link #expectedByMethod}
     * @param counted  amount the cashier counted per method
     */
    public void close(long actorUserId, Map<String, BigDecimal> expected, Map<String, BigDecimal> counted,
                      String notes, Instant closedAt) {
        requireOpenBy(actorUserId);
        Set<String> methods = new TreeSet<>(expected.keySet());
        methods.addAll(counted.keySet());

        List<CountLine> lines = new ArrayList<>();
        BigDecimal expectedSum = BigDecimal.ZERO;
        BigDecimal countedSum = BigDecimal.ZERO;
        for (String method : methods) {
            BigDecimal expectedAmount = money(expected.getOrDefault(method, BigDecimal.ZERO));
            BigDecimal countedAmount = money(counted.getOrDefault(method, BigDecimal.ZERO));
            lines.add(new CountLine(method, expectedAmount, countedAmount,
                    countedAmount.subtract(expectedAmount)));
            expectedSum = expectedSum.add(expectedAmount);
            countedSum = countedSum.add(countedAmount);
        }

        this.status = CashSessionStatus.CLOSED;
        this.closedAt = Objects.requireNonNull(closedAt, "closedAt is required");
        this.expectedTotal = money(expectedSum);
        this.countedTotal = money(countedSum);
        this.difference = money(countedSum.subtract(expectedSum));
        this.countDetail = lines;
        this.notes = notes;
    }

    /** Money is kept with two decimals, rounded half up. */
    static BigDecimal money(BigDecimal amount) {
        return Objects.requireNonNull(amount, "amount is required").setScale(2, RoundingMode.HALF_UP);
    }

    public Long getCashRegisterId() {
        return cashRegisterId;
    }

    public Long getUserId() {
        return userId;
    }

    public CashSessionStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public BigDecimal getOpeningAmount() {
        return openingAmount;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public BigDecimal getExpectedTotal() {
        return expectedTotal;
    }

    public BigDecimal getCountedTotal() {
        return countedTotal;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public List<CountLine> getCountDetail() {
        return countDetail == null ? List.of() : List.copyOf(countDetail);
    }

    public String getNotes() {
        return notes;
    }
}
