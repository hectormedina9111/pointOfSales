package com.medinasystems.pos.cash.application;

import com.medinasystems.pos.cash.domain.CashRegister;

public record CashRegisterView(Long id, Long branchId, String code, String name, String receiptPrefix,
                               boolean active) {

    public static CashRegisterView from(CashRegister register) {
        return new CashRegisterView(register.getId(), register.getBranchId(), register.getCode(), register.getName(),
                register.getReceiptPrefix(), register.isActive());
    }
}
