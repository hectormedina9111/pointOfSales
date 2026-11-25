package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.Map;

import com.medinasystems.pos.sales.domain.Payment;

/**
 * A payment of a sale. {@code cardLast4} is only the last four digits, never the card number. {@code creditNoteNumber} is
 * the number of the note paid with, when the method is CREDIT_NOTE.
 */
public record PaymentView(String paymentMethodCode, String paymentMethodName, BigDecimal amount,
                          BigDecimal amountReceived, String reference, String cardLast4, String creditNoteNumber) {

    /** @param creditNoteNumbers the numbers of the notes of the sale, by note id */
    public static PaymentView from(Payment payment, Map<Long, String> creditNoteNumbers) {
        String creditNoteNumber = payment.getCreditNoteId() == null
                ? null : creditNoteNumbers.get(payment.getCreditNoteId());
        return new PaymentView(payment.getPaymentMethod().getCode(), payment.getPaymentMethod().getName(),
                payment.getAmount(), payment.getAmountReceived(), payment.getReference(), payment.getCardLast4(),
                creditNoteNumber);
    }
}
