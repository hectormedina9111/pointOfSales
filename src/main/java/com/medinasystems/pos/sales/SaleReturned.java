package com.medinasystems.pos.sales;

import java.math.BigDecimal;
import java.util.List;

/**
 * A return document was created in this store, for a sale of this branch or of another one. It is published after the
 * return and its lines are saved, in the transaction of the return. The goods come back to the branch that receives the
 * return, which is the branch of the return document.
 *
 * @param returnSaleId the return document
 * @param branchId     the branch that received the return
 * @param userId       the user who created the return
 * @param lines        the lines that came back
 */
public record SaleReturned(long returnSaleId, long branchId, long userId, List<Line> lines) {

    /**
     * One line of the return document.
     *
     * @param saleLineId the id of the line of the return document
     * @param productId  the product returned
     * @param quantity      the quantity returned, positive
     * @param serialNumbers the serial numbers returned, one per unit, when the product has serial numbers (MANUAL or LIST); otherwise empty
     */
    public record Line(long saleLineId, long productId, BigDecimal quantity, List<String> serialNumbers) {
    }
}
