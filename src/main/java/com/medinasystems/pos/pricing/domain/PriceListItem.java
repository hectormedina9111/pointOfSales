package com.medinasystems.pos.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * The price of one product in one list, from a minimum quantity (a wholesale scale). Items are real
 * rows: deleting one removes it, because they are not catalogs. The list and the product are kept as ids.
 */
@Entity
@Table(name = "price_list_item")
public class PriceListItem extends AuditableEntity {

    private static final int QUANTITY_SCALE = 3;
    private static final int MONEY_SCALE = 2;

    @Column(name = "price_list_id", nullable = false)
    private Long priceListId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "min_quantity", nullable = false, precision = 12, scale = QUANTITY_SCALE)
    private BigDecimal minQuantity;

    @Column(nullable = false, precision = 14, scale = MONEY_SCALE)
    private BigDecimal price;

    protected PriceListItem() {
    }

    public PriceListItem(Long priceListId, Long productId, BigDecimal minQuantity, BigDecimal price) {
        this.priceListId = Objects.requireNonNull(priceListId, "priceListId is required");
        apply(productId, minQuantity, price);
    }

    /** The list cannot change; the product, the scale and the price can. */
    public void update(Long productId, BigDecimal minQuantity, BigDecimal price) {
        apply(productId, minQuantity, price);
    }

    private void apply(Long productId, BigDecimal minQuantity, BigDecimal price) {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(price, "price is required");
        if (minQuantity == null || minQuantity.compareTo(BigDecimal.ONE) < 0) {
            throw new BusinessRuleException("INVALID_MIN_QUANTITY", "The minimum quantity must be at least 1");
        }
        if (price.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", "The amount cannot be negative");
        }
        this.productId = productId;
        this.minQuantity = minQuantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
        this.price = price.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public Long getPriceListId() {
        return priceListId;
    }

    public Long getProductId() {
        return productId;
    }

    public BigDecimal getMinQuantity() {
        return minQuantity;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
