package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A sale (ticket). It starts ON_HOLD, with its lines and totals, and becomes PAID when it is charged: then it gets a
 * receipt number and its payments. A sale on hold may be deleted; a paid sale is a document and is never deleted.
 * Totals are stored as the calculator computed them, and the lines keep a copy of the product as it was sold.
 */
@Entity
@Table(name = "sale")
public class Sale extends AuditableEntity {

    /** The only sale type in 3A; returns (RETURN) come in 3B. */
    public static final String TYPE_SALE = "SALE";
    /** Ticket: a sale document without CAI. Fiscal invoices come in 3C. */
    public static final String DOCUMENT_TICKET = "TICKET";
    /** Fiscal status of a country without electronic invoicing for this document. */
    public static final String FISCAL_NOT_REQUIRED = "NOT_REQUIRED";

    @Column(nullable = false, length = 10)
    private String type = TYPE_SALE;

    @Column(name = "internal_number", nullable = false, unique = true)
    private Long internalNumber;

    @Column(name = "receipt_number", unique = true, length = 40)
    private String receiptNumber;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType = DOCUMENT_TICKET;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "cash_register_id", nullable = false)
    private Long cashRegisterId;

    @Column(name = "cash_session_id", nullable = false)
    private Long cashSessionId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType = OrderType.CASH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleStatus status;

    @Column(name = "is_exclusive", nullable = false)
    private boolean exclusive;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountTotal;

    @Column(name = "exempt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal exemptAmount;

    @Column(name = "taxable_amount_15", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount15;

    @Column(name = "taxable_amount_18", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount18;

    @Column(name = "tax_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = Money.ZERO;

    @Column(name = "change_due", nullable = false, precision = 14, scale = 2)
    private BigDecimal changeDue = Money.ZERO;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_authorized_by")
    private Long discountAuthorizedBy;

    /** Who authorized a price change in some line. Null when no price was changed. */
    @Column(name = "price_authorized_by")
    private Long priceAuthorizedBy;

    @Column(length = 500)
    private String notes;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "fiscal_status", nullable = false, length = 15)
    private String fiscalStatus = FISCAL_NOT_REQUIRED;

    @Column(name = "fiscal_number", unique = true, length = 30)
    private String fiscalNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fiscal_data")
    private Map<String, Object> fiscalData;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC")
    private List<SaleLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Payment> payments = new ArrayList<>();

    protected Sale() {
    }

    /** Starts a sale on hold with the totals of the calculator. Nothing is paid and there is no receipt yet. */
    public static Sale open(SaleContext context, long internalNumber, SaleTotals totals, SaleOrder order) {
        Sale sale = new Sale();
        sale.internalNumber = internalNumber;
        sale.status = SaleStatus.ON_HOLD;
        sale.replaceContent(context, totals, order);
        return sale;
    }

    /**
     * Replaces the customer, the order type, the lines, the totals and the notes of a sale on hold. The stored prices are
     * replaced by the ones given here, so the caller must have priced the lines.
     */
    public void reprice(SaleContext context, SaleTotals totals, SaleOrder order) {
        requireOnHold();
        replaceContent(context, totals, order);
    }

    /** Changes the notes of a sale on hold, without touching its lines or totals. */
    public void changeNotes(String notes) {
        requireOnHold();
        this.notes = notes;
    }

    /**
     * Charges the sale: it becomes PAID, gets its receipt number and idempotency key, and records the payments. The
     * payments must already be checked by {@link PaymentRules}.
     *
     * @param change the change given back, from {@link PaymentRules#check}
     */
    public void pay(String receiptNumber, String idempotencyKey, List<PaymentDraft> drafts, BigDecimal change,
                    Instant paidAt) {
        requireOnHold();
        this.status = SaleStatus.PAID;
        this.receiptNumber = receiptNumber;
        this.idempotencyKey = idempotencyKey;
        this.paidAt = paidAt;
        BigDecimal paid = Money.ZERO;
        for (PaymentDraft draft : drafts) {
            payments.add(new Payment(this, draft, paidAt));
            paid = paid.add(draft.amount());
        }
        this.amountPaid = Money.scale2(paid);
        this.changeDue = Money.scale2(change);
    }

    private void replaceContent(SaleContext context, SaleTotals totals, SaleOrder order) {
        this.branchId = context.branchId();
        this.cashRegisterId = context.cashRegisterId();
        this.cashSessionId = context.cashSessionId();
        this.cashierId = context.cashierId();
        this.customerId = order.customerId();
        this.orderType = order.orderType();
        this.exclusive = totals.exclusive();
        this.subtotal = totals.subtotal();
        this.discountTotal = totals.discountTotal();
        this.exemptAmount = totals.exemptAmount();
        this.taxableAmount15 = totals.taxableAmount15();
        this.taxableAmount18 = totals.taxableAmount18();
        this.taxTotal = totals.taxTotal();
        this.total = totals.total();
        this.discountType = totals.saleDiscount() == null ? null : totals.saleDiscount().type();
        this.discountValue = totals.saleDiscount() == null ? null : totals.saleDiscount().value();
        this.discountAuthorizedBy = order.discountAuthorizedBy();
        this.priceAuthorizedBy = order.priceAuthorizedBy();
        this.notes = order.notes();
        this.lines.clear();
        for (CalculatedLine line : totals.lines()) {
            lines.add(new SaleLine(this, line));
        }
    }

    private void requireOnHold() {
        if (status != SaleStatus.ON_HOLD) {
            throw new BusinessRuleException("SALE_NOT_ON_HOLD", "Only a sale on hold can be changed or paid");
        }
    }

    /**
     * The part of the total that is taxed at a rate other than 0, 15 and 18. It is not stored: the total minus the
     * other buckets and the tax gives it exactly.
     */
    public BigDecimal getOtherTaxableAmount() {
        return Money.scale2(total.subtract(exemptAmount).subtract(taxableAmount15).subtract(taxableAmount18)
                .subtract(taxTotal));
    }

    public String getType() {
        return type;
    }

    public Long getInternalNumber() {
        return internalNumber;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getDocumentType() {
        return documentType;
    }

    public Long getBranchId() {
        return branchId;
    }

    public Long getCashRegisterId() {
        return cashRegisterId;
    }

    public Long getCashSessionId() {
        return cashSessionId;
    }

    public Long getCashierId() {
        return cashierId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDiscountTotal() {
        return discountTotal;
    }

    public BigDecimal getExemptAmount() {
        return exemptAmount;
    }

    public BigDecimal getTaxableAmount15() {
        return taxableAmount15;
    }

    public BigDecimal getTaxableAmount18() {
        return taxableAmount18;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public BigDecimal getChangeDue() {
        return changeDue;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public Long getDiscountAuthorizedBy() {
        return discountAuthorizedBy;
    }

    public Long getPriceAuthorizedBy() {
        return priceAuthorizedBy;
    }

    public String getNotes() {
        return notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFiscalStatus() {
        return fiscalStatus;
    }

    public String getFiscalNumber() {
        return fiscalNumber;
    }

    public Map<String, Object> getFiscalData() {
        return fiscalData;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public List<SaleLine> getLines() {
        return List.copyOf(lines);
    }

    public List<Payment> getPayments() {
        return List.copyOf(payments);
    }
}
