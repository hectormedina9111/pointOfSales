package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * A line a return asks for: a line of the original sale, the quantity that comes back, and for a product that tracks serial
 * numbers, the serial numbers of the units that come back (one per unit).
 */
public record ReturnLineRequest(long saleLineId, BigDecimal quantity, List<String> serialNumbers) {

    public ReturnLineRequest {
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
    }

    /** A line without serial numbers, for products that do not track them. */
    public ReturnLineRequest(long saleLineId, BigDecimal quantity) {
        this(saleLineId, quantity, List.of());
    }
}
