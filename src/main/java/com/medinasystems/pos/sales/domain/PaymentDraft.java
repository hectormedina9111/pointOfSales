package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One payment of a sale, with its method already found. {@code amountReceived} is only set for a method that gives
 * change; {@code cardLast4} is the last four digits of a card, never the full number.
 */
public record PaymentDraft(PaymentMethod method, BigDecimal amount, BigDecimal amountReceived, String reference,
                           String cardLast4) {

    public PaymentDraft {
        Objects.requireNonNull(method, "payment method is required");
        amount = amount == null ? null : Money.scale2(amount);
        amountReceived = amountReceived == null ? null : Money.scale2(amountReceived);
        reference = blankToNull(reference);
        cardLast4 = blankToNull(cardLast4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
