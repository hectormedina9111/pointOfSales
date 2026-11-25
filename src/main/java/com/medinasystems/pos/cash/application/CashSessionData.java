package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.cash.domain.CashMovementType;

/** Input of the cash session operations. */
public final class CashSessionData {

    private CashSessionData() {
    }

    /** Opens a session on a register with the cash float. */
    public record Open(long cashRegisterId, BigDecimal openingAmount) {
    }

    /** A deposit or a withdrawal. The authorization is required for a withdrawal unless the cashier holds the privilege. */
    public record Movement(CashMovementType type, BigDecimal amount, String reason, Authorization authorization) {
    }

    /** Credentials of the manager who authorizes a withdrawal. The password is never printed. */
    public record Authorization(String username, String password) {

        @Override
        public String toString() {
            return "Authorization[username=" + username + ", password=***]";
        }
    }

    /** Amount counted for one payment method at closing. */
    public record Count(String paymentMethodCode, BigDecimal amount) {
    }

    /** The count and the notes of the closing. */
    public record Close(List<Count> counts, String notes) {
    }
}
