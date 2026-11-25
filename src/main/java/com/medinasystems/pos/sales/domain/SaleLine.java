package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * One line of a sale. It keeps a copy of the product as it was sold (sku, description, price, tax) so that later
 * changes to the catalog never alter a document. {@code subtotal + taxAmount = total}.
 */
@Entity
@Table(name = "sale_line")
public class SaleLine extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "line_number", nullable = false)
    private Short lineNumber;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_code", nullable = false, length = 20)
    private String taxCode;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "price_list_id")
    private Long priceListId;

    protected SaleLine() {
    }

    SaleLine(Sale sale, CalculatedLine calculated) {
        PricedLine line = calculated.line();
        this.sale = sale;
        this.lineNumber = (short) calculated.lineNumber();
        this.productId = line.productId();
        this.sku = line.sku();
        this.description = line.description();
        this.quantity = line.quantity();
        this.unitPrice = line.unitPrice();
        this.unitCost = line.unitCost();
        this.discountType = line.discount() == null ? null : line.discount().type();
        this.discountValue = line.discount() == null ? null : line.discount().value();
        this.discountAmount = calculated.discountAmount();
        this.taxCode = line.taxCode();
        this.taxRate = line.taxRate();
        this.taxAmount = calculated.taxAmount();
        this.subtotal = calculated.subtotal();
        this.total = calculated.total();
        this.priceListId = line.priceListId();
    }

    public Short getLineNumber() {
        return lineNumber;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Long getPriceListId() {
        return priceListId;
    }
}
