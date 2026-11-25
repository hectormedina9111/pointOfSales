package com.medinasystems.pos.cash;

import java.util.Optional;

/** Public API of the cash module for the sales tickets: the name of a cash register. */
public interface CashRegisterDirectory {

    /** The register with what a ticket prints from it. Empty when the register does not exist. */
    Optional<CashRegisterInfo> find(long cashRegisterId);
}
