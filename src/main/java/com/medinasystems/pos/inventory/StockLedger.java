package com.medinasystems.pos.inventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public API of the inventory module for the other modules (sales, for example). It is the only way to move stock: it
 * checks the product, the branch and the negative stock rule, updates the stock and its average cost, and writes the
 * movement. Each call must run inside a transaction, because the stock row is locked until the transaction ends.
 *
 * <p>A LIST product (serial mode) moves one unit per serial number: the serial list has one entry per unit, and the units are
 * tracked one by one. A MANUAL product moves by quantity, in whole units, and refuses serial numbers with
 * {@code SERIALS_NOT_ALLOWED}; its serials belong to the sale (see the inventory documentation in the plan).
 */
public interface StockLedger {

    /**
     * Applies one movement. The sign of {@code signedQuantity} must match the type: incoming types (ENTRY, RETURN_IN,
     * TRANSFER_IN) take a positive quantity, outgoing types (EXIT, SALE_OUT, TRANSFER_OUT) a negative one.
     *
     * @param unitCost      the cost of an incoming ENTRY or TRANSFER_IN; null to use the average or the product cost.
     *                      Ignored for the other types, which move at the current average cost.
     * @param serialNumbers the serial numbers of a LIST product, one per unit; must be empty for a MANUAL product
     * @throws com.medinasystems.pos.shared.BusinessRuleException with code {@code INSUFFICIENT_STOCK} when an outgoing
     *         movement would leave a negative balance in a branch that does not allow it, {@code SERIAL_NOT_AVAILABLE}
     *         when an outgoing unit is not available in the branch, {@code SERIAL_ALREADY_EXISTS} when an incoming unit
     *         is still available elsewhere, or a serial-count rule
     */
    StockMovement apply(long branchId, long productId, StockMovementType type, BigDecimal signedQuantity,
                        BigDecimal unitCost, List<String> serialNumbers, StockReferenceType referenceType,
                        long referenceId, long userId);

    /**
     * Sets the stock of the product in the branch to a counted quantity. For a product without serials this is one
     * ADJUSTMENT for the difference. For a product with serials, the serial numbers are the full list of units that are
     * physically present: the units missing from the list leave (one movement), and the listed units that are not
     * available here enter (another movement).
     *
     * @return the movements, empty when nothing changed
     */
    List<StockMovement> count(long branchId, long productId, BigDecimal countedQuantity, List<String> serialNumbers,
                              StockReferenceType referenceType, long referenceId, long userId);
}
