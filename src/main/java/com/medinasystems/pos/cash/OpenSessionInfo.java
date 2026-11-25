package com.medinasystems.pos.cash;

/**
 * An open cash session, as a sale needs it: the session, its register and branch, and the prefix of the receipts
 * of the register.
 */
public record OpenSessionInfo(long sessionId, long cashRegisterId, long branchId, String receiptPrefix) {
}
