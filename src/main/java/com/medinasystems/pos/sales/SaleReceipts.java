package com.medinasystems.pos.sales;

import java.util.Collection;
import java.util.Map;

/**
 * Public API of the sales module for the other modules. It gives the receipt numbers of sales and returns, so that they
 * can show them without reading the sales tables.
 */
public interface SaleReceipts {

    /**
     * The receipt numbers of the given sales or returns. Ids that do not exist, and sales without a receipt (holds), are
     * left out.
     */
    Map<Long, String> receiptNumbers(Collection<Long> saleIds);
}
