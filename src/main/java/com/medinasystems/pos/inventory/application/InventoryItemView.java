package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.medinasystems.pos.inventory.domain.InventoryItem;

/** The stock of a product in a branch, as the stock list shows it. {@code belowMin} is true under the minimum. */
public record InventoryItemView(long id, long productId, String productSku, String productName, Long categoryId,
                                long branchId, String branchName, BigDecimal quantity, BigDecimal averageCost,
                                BigDecimal minStock, boolean belowMin, Instant lastMovementAt) {

    static InventoryItemView of(InventoryItem item, InventoryNames names) {
        return new InventoryItemView(item.getId(), item.getProductId(), item.getProductSku(), item.getProductName(),
                item.getCategoryId(), item.getBranchId(), names.branchName(item.getBranchId()), item.getQuantity(),
                item.getAverageCost(), item.getMinStock(), item.isBelowMinimum(), item.getLastMovementAt());
    }
}
