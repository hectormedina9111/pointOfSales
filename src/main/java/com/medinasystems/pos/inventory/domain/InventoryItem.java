package com.medinasystems.pos.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.shared.AuditableEntity;

/**
 * The stock of one product in one branch, with its weighted average cost. The product copies (sku, name, category and
 * minimum stock) are refreshed on every movement, so the stock lists are read from this table alone.
 */
@Entity
@Table(name = "inventory_item")
public class InventoryItem extends AuditableEntity {

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "branch_id", nullable = false)
    private long branchId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "average_cost", nullable = false)
    private BigDecimal averageCost;

    @Column(name = "product_sku", nullable = false, length = 40)
    private String productSku;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "min_stock", nullable = false)
    private BigDecimal minStock;

    @Column(name = "last_movement_at")
    private Instant lastMovementAt;

    protected InventoryItem() {
    }

    /** Stores the state after a movement and refreshes the product copies from the catalog. */
    public void applyMovement(BigDecimal newQuantity, BigDecimal newAverageCost, SaleableProduct product,
                              Instant movedAt) {
        this.quantity = newQuantity;
        this.averageCost = newAverageCost;
        this.productSku = product.sku();
        this.productName = product.name();
        this.categoryId = product.categoryId();
        this.minStock = product.minStock();
        this.lastMovementAt = movedAt;
    }

    /** True when the stock is below a minimum that is set: a zero minimum means no alert. */
    public boolean isBelowMinimum() {
        return minStock.signum() > 0 && quantity.compareTo(minStock) < 0;
    }

    public long getProductId() {
        return productId;
    }

    public long getBranchId() {
        return branchId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public Instant getLastMovementAt() {
        return lastMovementAt;
    }
}
