package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.sales.domain.RefundMethod;

/**
 * What can be returned from a sale: its lines with the quantities still left, and its payments with what was refunded.
 * {@code sameDay} tells the screen whether the refund is in cash or by the original method, or a credit note.
 */
public record ReturnableView(Long saleId, String receiptNumber, Long customerId, String customerName, Instant paidAt,
                             boolean sameDay, List<RefundMethod> allowedRefundMethods, List<PaymentRefund> payments,
                             List<Line> lines) {

    /** One payment method of the sale: what was paid, what was refunded by earlier returns, and what is left. */
    public record PaymentRefund(String paymentMethodCode, String paymentMethodName, BigDecimal paid,
                                BigDecimal refunded, BigDecimal refundable) {
    }

    /** One line of the sale: the quantities and the amount refunded so far. */
    public record Line(Long saleLineId, Long productId, String productName, BigDecimal quantity,
                       BigDecimal returnedQuantity, BigDecimal returnableQuantity, BigDecimal netTotal,
                       BigDecimal refundedAmount) {
    }
}
