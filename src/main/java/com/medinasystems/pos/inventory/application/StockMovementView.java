package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.domain.InventoryMovement;

/**
 * A movement of the log as the movement screen shows it. {@code referenceNumber} is the number of the inventory document
 * when the movement comes from one, and the receipt number when it comes from a sale or a return; null otherwise.
 */
public record StockMovementView(long id, long branchId, long productId, String productName, StockMovementType type,
                                BigDecimal quantity, BigDecimal resultingBalance, BigDecimal unitCost,
                                BigDecimal resultingAverageCost, StockReferenceType referenceType, long referenceId,
                                String referenceNumber, Instant movedAt, long userId, String userName) {

    /**
     * @param receiptNumbers the receipt numbers of the sales and returns of the page, by id
     */
    static StockMovementView of(InventoryMovement movement, InventoryNames names, Map<Long, String> receiptNumbers) {
        String referenceNumber = switch (movement.getReferenceType()) {
            case INVENTORY_DOCUMENT -> names.documentNumber(movement.getReferenceId());
            case SALE, SALE_RETURN -> receiptNumbers.get(movement.getReferenceId());
            case TRANSFER -> null;
        };
        return new StockMovementView(movement.getId(), movement.getBranchId(), movement.getProductId(),
                names.productName(movement.getProductId()), movement.getType(), movement.getQuantity(),
                movement.getResultingBalance(), movement.getUnitCost(), movement.getResultingAverageCost(),
                movement.getReferenceType(), movement.getReferenceId(), referenceNumber, movement.getMovedAt(),
                movement.getUserId(), names.userName(movement.getUserId()));
    }
}
