package com.medinasystems.pos.sales.domain;

/** Where and by whom a sale is made: the branch, the register, the open cash session and the cashier. */
public record SaleContext(long branchId, long cashRegisterId, long cashSessionId, long cashierId) {
}
