package com.medinasystems.pos.cash;

/** What a ticket prints about a cash register. */
public record CashRegisterInfo(long cashRegisterId, long branchId, String code, String name, String receiptPrefix) {
}
