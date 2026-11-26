package com.medinasystems.pos.sales;

import java.time.Instant;
import java.util.List;

/**
 * Public API of the sales module for the other modules. It finds the sales of the products whose serial numbers are typed
 * at the sale (serial mode MANUAL), so that the inventory can show them when someone looks up a serial number.
 */
public interface SaleSerials {

    /**
     * The sales where a MANUAL product sold this serial number, oldest first. The comparison ignores case. A unit that came
     * back has {@code returned} true. Returns and LIST products are left out.
     */
    List<ManualSerialSale> manualSerialSales(String serialNumber);

    /**
     * One sale of a unit with this serial number.
     *
     * @param productId     the product sold
     * @param saleId        the sale
     * @param receiptNumber the receipt of the sale
     * @param branchId      the branch that sold the unit
     * @param soldAt        when the sale was charged
     * @param returned      true once the unit came back on a return
     */
    record ManualSerialSale(long productId, long saleId, String receiptNumber, long branchId, Instant soldAt,
                            boolean returned) {
    }
}
