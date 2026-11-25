package com.medinasystems.pos.inventory;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Public API of the inventory module for the other modules (sales, for example). It is the only way to move stock: it
 * checks the product, the branch and the negative stock rule, updates the stock and its average cost, and writes the
 * movement. Each call must run inside a transaction, because the stock row is locked until the transaction ends.
 */
public interface StockLedger {

    /**
     * Applies one movement. The sign of {@code signedQuantity} must match the type: incoming types (ENTRY, RETURN_IN,
     * TRANSFER_IN) take a positive quantity, outgoing types (EXIT, SALE_OUT, TRANSFER_OUT) a negative one.
     *
     * @param unitCost the cost of an incoming ENTRY or TRANSFER_IN; null to use the average or the product cost.
     *                 Ignored for the other types, which move at the current average cost.
     * @throws com.medinasystems.pos.shared.BusinessRuleException with code {@code INSUFFICIENT_STOCK} when an outgoing
     *         movement would leave a negative balance in a branch that does not allow it
     */
    StockMovement apply(long branchId, long productId, StockMovementType type, BigDecimal signedQuantity,
                        BigDecimal unitCost, StockReferenceType referenceType, long referenceId, long userId);

    /**
     * Sets the stock of the product in the branch to a counted quantity, as an ADJUSTMENT for the difference.
     *
     * @return the movement, or empty when the counted quantity equals the stock, so nothing moved
     */
    Optional<StockMovement> count(long branchId, long productId, BigDecimal countedQuantity,
                                  StockReferenceType referenceType, long referenceId, long userId);
}
