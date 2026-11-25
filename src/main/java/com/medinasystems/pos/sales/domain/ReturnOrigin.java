package com.medinasystems.pos.sales.domain;

/**
 * The sale a return refunds, as the return keeps it: the id and receipt number of the sale, and the fields a return
 * copies from it (customer, order type, exclusive). The sale is the one of this node, or, for a sale of another branch,
 * the one the central node reports: a return keeps the id and receipt number without a local row for the sale.
 */
public record ReturnOrigin(long saleId, String receiptNumber, Long customerId, OrderType orderType,
                           boolean exclusive) {

    public static ReturnOrigin of(Sale sale) {
        return new ReturnOrigin(sale.getId(), sale.getReceiptNumber(), sale.getCustomerId(), sale.getOrderType(),
                sale.isExclusive());
    }
}
