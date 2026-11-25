package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;

/** A line the cashier wants to return: a line of the original sale and the quantity that comes back. */
public record ReturnLineRequest(long saleLineId, BigDecimal quantity) {
}
