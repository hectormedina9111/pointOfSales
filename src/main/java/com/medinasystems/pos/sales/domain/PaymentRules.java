package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * Checks the payments of a sale against its total. The payments must add up to the total exactly, and each one must
 * follow its method: an active method, a reference when the method asks for one, an amount received only when the
 * method gives change, and at most a card's last four digits.
 */
public final class PaymentRules {

    private PaymentRules() {
    }

    /**
     * @return the change to give back: the sum of {@code amountReceived - amount} over the payments that received an
     *         amount
     * @throws BusinessRuleException PAYMENTS_REQUIRED, PAYMENT_METHOD_INACTIVE, INVALID_PAYMENT_AMOUNT,
     *                               PAYMENT_REFERENCE_REQUIRED, AMOUNT_RECEIVED_NOT_ALLOWED, AMOUNT_RECEIVED_TOO_LOW,
     *                               INVALID_CARD_LAST4 or PAYMENTS_DO_NOT_MATCH_TOTAL
     */
    public static BigDecimal check(BigDecimal total, List<PaymentDraft> payments) {
        if (payments == null || payments.isEmpty()) {
            throw new BusinessRuleException("PAYMENTS_REQUIRED", "The sale needs at least one payment");
        }
        BigDecimal paid = Money.ZERO;
        BigDecimal change = Money.ZERO;
        for (PaymentDraft payment : payments) {
            PaymentMethod method = payment.method();
            if (!method.isActive()) {
                throw new BusinessRuleException("PAYMENT_METHOD_INACTIVE", "The payment method is inactive");
            }
            if (payment.amount() == null || payment.amount().signum() <= 0) {
                throw new BusinessRuleException("INVALID_PAYMENT_AMOUNT", "The payment amount must be greater than zero");
            }
            if (method.isRequiresReference() && payment.reference() == null) {
                throw new BusinessRuleException("PAYMENT_REFERENCE_REQUIRED", "This payment method needs a reference");
            }
            if (payment.amountReceived() != null) {
                if (!method.isAllowsChange()) {
                    throw new BusinessRuleException("AMOUNT_RECEIVED_NOT_ALLOWED",
                            "Only a method that gives change can receive an amount");
                }
                if (payment.amountReceived().compareTo(payment.amount()) < 0) {
                    throw new BusinessRuleException("AMOUNT_RECEIVED_TOO_LOW",
                            "The amount received is less than the payment");
                }
                change = change.add(payment.amountReceived().subtract(payment.amount()));
            }
            if (payment.cardLast4() != null && !payment.cardLast4().matches("[0-9]{4}")) {
                throw new BusinessRuleException("INVALID_CARD_LAST4", "The card must have exactly four digits");
            }
            paid = paid.add(payment.amount());
        }
        if (paid.compareTo(total) != 0) {
            throw new BusinessRuleException("PAYMENTS_DO_NOT_MATCH_TOTAL", "The payments must add up to the total");
        }
        return Money.scale2(change);
    }
}
