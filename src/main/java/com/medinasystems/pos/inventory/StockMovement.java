package com.medinasystems.pos.inventory;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A movement as other modules see it: the change with its sign, the balance and the average cost after it, and the
 * document or sale that caused it.
 */
public record StockMovement(long id, long branchId, long productId, StockMovementType type, BigDecimal quantity,
                            BigDecimal resultingBalance, BigDecimal unitCost, BigDecimal resultingAverageCost,
                            StockReferenceType referenceType, long referenceId, Instant movedAt, long userId) {

    /** The balance before this movement: the balance after it minus the change. */
    public BigDecimal previousBalance() {
        return resultingBalance.subtract(quantity);
    }
}
