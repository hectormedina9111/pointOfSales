package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;

import com.medinasystems.pos.sales.domain.Payment;

/** A payment of a sale. {@code cardLast4} is only the last four digits, never the card number. */
public record PaymentView(String paymentMethodCode, String paymentMethodName, BigDecimal amount,
                          BigDecimal amountReceived, String reference, String cardLast4) {

    public static PaymentView from(Payment payment) {
        return new PaymentView(payment.getPaymentMethod().getCode(), payment.getPaymentMethod().getName(),
                payment.getAmount(), payment.getAmountReceived(), payment.getReference(), payment.getCardLast4());
    }
}
