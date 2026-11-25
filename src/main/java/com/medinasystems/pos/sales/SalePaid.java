package com.medinasystems.pos.sales;

import java.math.BigDecimal;
import java.util.List;

/**
 * A sale was charged in this store, directly or from a hold. It is published after the sale and its lines are saved with
 * their ids, in the transaction of the charge. The central node never publishes it: it inserts the documents that stores
 * upload.
 *
 * @param saleId   the sale
 * @param branchId the branch that sold
 * @param userId   the cashier who charged it
 * @param lines    the lines that were sold
 */
public record SalePaid(long saleId, long branchId, long userId, List<Line> lines) {

    /**
     * One line of the sale.
     *
     * @param saleLineId the id of the line
     * @param productId  the product sold
     * @param quantity      the quantity sold, positive
     * @param serialNumbers the serial numbers sold, one per unit, when the product tracks them; otherwise empty
     */
    public record Line(long saleLineId, long productId, BigDecimal quantity, List<String> serialNumbers) {
    }
}
