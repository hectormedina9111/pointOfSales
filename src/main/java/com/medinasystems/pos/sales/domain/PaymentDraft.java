package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One payment of a sale, with its method already found. {@code amountReceived} is only set for a method that gives
 * change; {@code cardLast4} is the last four digits of a card, never the full number. For a credit note, the number is
 * what the cashier typed; the id and the redemption key are set once the central node has taken the balance.
 */
public record PaymentDraft(PaymentMethod method, BigDecimal amount, BigDecimal amountReceived, String reference,
                           String cardLast4, String creditNoteNumber, Long creditNoteId, String redemptionKey) {

    public PaymentDraft(PaymentMethod method, BigDecimal amount, BigDecimal amountReceived, String reference,
                        String cardLast4) {
        this(method, amount, amountReceived, reference, cardLast4, null, null, null);
    }

    public PaymentDraft {
        Objects.requireNonNull(method, "payment method is required");
        amount = amount == null ? null : Money.scale2(amount);
        amountReceived = amountReceived == null ? null : Money.scale2(amountReceived);
        reference = blankToNull(reference);
        cardLast4 = blankToNull(cardLast4);
        creditNoteNumber = blankToNull(creditNoteNumber);
        redemptionKey = blankToNull(redemptionKey);
    }

    /** The same payment, once the balance of its credit note has been taken under {@code redemptionKey}. */
    public PaymentDraft withCreditNote(long noteId, String key) {
        return new PaymentDraft(method, amount, amountReceived, reference, cardLast4, creditNoteNumber, noteId, key);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
