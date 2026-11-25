package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    @Column(name = "list_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "price_overridden", nullable = false)
    private boolean priceOverridden;

    /** Quantity already returned from this line of a SALE. Always between zero and the quantity. */
    @Column(name = "returned_quantity", nullable = false, precision = 12, scale = 3)
    private BigDecimal returnedQuantity = NO_QUANTITY;

    /** For a line of a RETURN: the line of the original sale. Not a foreign key: it may live in another store. */
    @Column(name = "original_line_id")
    private Long originalLineId;

    /** The serial numbers of the line. In a sale they are sold; in a return they come back, marked as returned. */
    @OneToMany(mappedBy = "saleLine", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SaleLineSerial> serials = new ArrayList<>();

    private static final BigDecimal NO_QUANTITY = new BigDecimal("0.000");

    protected SaleLine() {
    }

    /** A line of a return: the product, price and tax are copied from the original line, the amounts are refunded ones. */
    SaleLine(Sale sale, int lineNumber, ReturnSourceLine original, CalculatedReturnLine calculated) {
        this.sale = sale;
        this.lineNumber = (short) lineNumber;
        this.productId = original.productId();
        this.sku = original.sku();
        this.description = original.description();
        this.quantity = calculated.quantity();
        this.unitPrice = original.unitPrice();
        this.unitCost = original.unitCost();
        this.discountAmount = Money.ZERO;
        this.taxCode = original.taxCode();
        this.taxRate = original.taxRate();
        this.taxAmount = calculated.taxAmount();
        this.subtotal = calculated.subtotal();
        this.total = calculated.total();
        this.priceListId = original.priceListId();
        this.listPrice = original.listPrice();
        this.priceOverridden = original.priceOverridden();
        this.originalLineId = original.id();
        for (String number : calculated.serialNumbers()) {
            serials.add(new SaleLineSerial(this, number, true));
        }
    }

    /** Adds a quantity returned from this line. The caller has checked that it does not exceed what is left. */
    void addReturned(BigDecimal returned) {
        this.returnedQuantity = this.returnedQuantity.add(returned);
    }

    /** True when every unit of the line has been returned. */
    boolean isFullyReturned() {
        return returnedQuantity.compareTo(quantity) >= 0;
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
        this.listPrice = line.listPrice();
        this.priceOverridden = line.priceOverridden();
        for (String number : line.serialNumbers()) {
            serials.add(new SaleLineSerial(this, number, false));
        }
    }

    /** Marks these serial numbers of the line as returned. The caller has checked that the line sold them. */
    public void markSerialsReturned(List<String> numbers) {
        for (SaleLineSerial serial : serials) {
            if (numbers.contains(serial.getSerialNumber())) {
                serial.markReturned();
            }
        }
    }

    /** Every serial number of the line, in the order they were stored. */
    public List<String> getSerialNumbers() {
        return serials.stream().map(SaleLineSerial::getSerialNumber).toList();
    }

    /** The serial rows of the line, for the upload. */
    public List<SaleLineSerial> getSerials() {
        return List.copyOf(serials);
    }

    /** The serial numbers of the line that came back. */
    public List<String> getReturnedSerialNumbers() {
        return serials.stream().filter(SaleLineSerial::isReturned).map(SaleLineSerial::getSerialNumber).toList();
    }

    /** The serial numbers of the line that did not come back yet. */
    public List<String> getReturnableSerialNumbers() {
        return serials.stream().filter(serial -> !serial.isReturned()).map(SaleLineSerial::getSerialNumber).toList();
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

    public BigDecimal getListPrice() {
        return listPrice;
    }

    public boolean isPriceOverridden() {
        return priceOverridden;
    }

    public BigDecimal getReturnedQuantity() {
        return returnedQuantity;
    }

    public Long getOriginalLineId() {
        return originalLineId;
    }
}
