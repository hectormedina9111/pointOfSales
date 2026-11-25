package com.medinasystems.pos.cash.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.CashRegisterDirectory;
import com.medinasystems.pos.cash.CashRegisterInfo;
import com.medinasystems.pos.cash.infrastructure.CashRegisterRepository;

/** Answers {@link CashRegisterDirectory} from the cash register table. */
@Component
public class DefaultCashRegisterDirectory implements CashRegisterDirectory {

    private final CashRegisterRepository registers;

    public DefaultCashRegisterDirectory(CashRegisterRepository registers) {
        this.registers = registers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegisterInfo> find(long cashRegisterId) {
        return registers.findById(cashRegisterId).map(register -> new CashRegisterInfo(register.getId(),
                register.getBranchId(), register.getCode(), register.getName(), register.getReceiptPrefix()));
    }
}
