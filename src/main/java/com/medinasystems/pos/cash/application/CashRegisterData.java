package com.medinasystems.pos.cash.application;

/** Input for creating or changing a cash register. */
public record CashRegisterData(Long branchId, String code, String name, String receiptPrefix, boolean active) {
}
