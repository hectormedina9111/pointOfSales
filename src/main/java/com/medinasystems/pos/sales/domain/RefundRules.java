package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * Decides how a return is refunded. On the day of the sale the refunds are given now, in cash or with a method the sale
 * was paid with, and they must add up to the return total. On a later day nothing is refunded in cash: a credit note is
 * issued instead.
 */
public final class RefundRules {

    private static final String CASH = "CASH";

    private RefundRules() {
    }

    /**
     * @param total          the amount the return refunds
     * @param sameDay        true when the return is on the same day as the sale
     * @param refunds        the refunds asked for: they must be empty when the return is not on the same day
     * @param paidByMethod   the amount the sale was paid with, per method code
     * @param refundedByMethod the amount already refunded by earlier returns, per method code
     * @return the refund method: CREDIT_NOTE when not on the same day, otherwise CASH or ORIGINAL_METHOD
     * @throws BusinessRuleException REFUNDS_REQUIRED, REFUND_METHOD_NOT_ALLOWED, REFUND_EXCEEDS_PAID,
     *                               RETURN_REFUND_MISMATCH, or the payment rules (INVALID_PAYMENT_AMOUNT,
     *                               PAYMENT_METHOD_INACTIVE, PAYMENT_REFERENCE_REQUIRED)
     */
    public static RefundMethod check(BigDecimal total, boolean sameDay, List<PaymentDraft> refunds,
                                     Map<String, BigDecimal> paidByMethod, Map<String, BigDecimal> refundedByMethod) {
        boolean given = refunds != null && !refunds.isEmpty();
        if (!sameDay) {
            if (given) {
                throw new BusinessRuleException("REFUND_METHOD_NOT_ALLOWED",
                        "A return after the day of the sale is refunded with a credit note only");
            }
            return RefundMethod.CREDIT_NOTE;
        }
        if (!given) {
            throw new BusinessRuleException("REFUNDS_REQUIRED", "The return needs at least one refund");
        }
        for (PaymentDraft refund : refunds) {
            // A credit note is paid with a balance, never given back: refunds are cash or the original method.
            if (PaymentRules.CREDIT_NOTE.equals(refund.method().getCode())) {
                throw new BusinessRuleException("REFUND_METHOD_NOT_ALLOWED",
                        "A return cannot be refunded with a credit note");
            }
        }

        // The method, the amount and the reference follow the payment rules; the sum is checked against the total below.
        BigDecimal refundSum = Money.ZERO;
        for (PaymentDraft refund : refunds) {
            if (refund.amount() != null) {
                refundSum = refundSum.add(refund.amount());
            }
        }
        PaymentRules.check(refundSum, refunds);

        boolean allCash = true;
        BigDecimal refunded = Money.ZERO;
        for (PaymentDraft refund : refunds) {
            String code = refund.method().getCode();
            if (!CASH.equals(code)) {
                if (!paidByMethod.containsKey(code)) {
                    throw new BusinessRuleException("REFUND_METHOD_NOT_ALLOWED",
                            "The sale was not paid with this method");
                }
                BigDecimal available = paidByMethod.get(code).subtract(refundedByMethod.getOrDefault(code, Money.ZERO));
                if (refund.amount().compareTo(available) > 0) {
                    throw new BusinessRuleException("REFUND_EXCEEDS_PAID",
                            "The refund is more than the amount paid with this method");
                }
                allCash = false;
            }
            refunded = refunded.add(refund.amount());
        }
        if (refunded.compareTo(total) != 0) {
            throw new BusinessRuleException("RETURN_REFUND_MISMATCH", "The refunds must add up to the return total");
        }
        return allCash ? RefundMethod.CASH : RefundMethod.ORIGINAL_METHOD;
    }
}
