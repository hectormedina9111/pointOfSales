package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.sales.domain.Payment;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.RefundMethod;
import com.medinasystems.pos.sales.domain.ReturnSourceLine;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleLine;

/**
 * What can be returned from a sale: its lines with the quantities still left, and its payments with what was refunded.
 * {@code sameDay} tells the screen whether the refund is in cash or by the original method, or a credit note.
 *
 * <p>{@code branchId} and {@code branchName} say where the sale was made. {@code foreign} is true when the sale is not in
 * this node's database: the view then comes from the central node. The central node builds the same view for a store, so
 * the store computes the same amounts for a sale of another branch as for its own.
 */
public record ReturnableView(Long saleId, String receiptNumber, Long customerId, String customerName, Instant paidAt,
                             boolean sameDay, List<RefundMethod> allowedRefundMethods, List<PaymentRefund> payments,
                             List<Line> lines, Long branchId, String branchName, boolean foreign, OrderType orderType,
                             boolean exclusive) {

    /**
     * Builds the view of a sale. {@code held} is the quantity per line that other claims hold (see
     * {@link com.medinasystems.pos.sales.infrastructure.SaleReturnClaimRepository#sumHeldByLine}).
     */
    public static ReturnableView of(Sale sale, boolean sameDay, String customerName, String branchName, boolean foreign,
                                    Map<Long, BigDecimal> refundedByLine, Map<String, BigDecimal> refundedByMethod,
                                    Map<Long, BigDecimal> held) {
        Map<String, BigDecimal> paid = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (Payment payment : sale.getPayments()) {
            String code = payment.getPaymentMethod().getCode();
            paid.merge(code, payment.getAmount(), BigDecimal::add);
            names.putIfAbsent(code, payment.getPaymentMethod().getName());
        }
        List<PaymentRefund> methods = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : paid.entrySet()) {
            BigDecimal refund = refundedByMethod.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            methods.add(new PaymentRefund(entry.getKey(), names.get(entry.getKey()), entry.getValue(), refund,
                    entry.getValue().subtract(refund)));
        }
        List<Line> lines = new ArrayList<>();
        for (SaleLine line : sale.getLines()) {
            ReturnSourceLine source = ReturnSourceLine.of(line, held.getOrDefault(line.getId(), BigDecimal.ZERO));
            lines.add(Line.of(source, refundedByLine.getOrDefault(line.getId(), BigDecimal.ZERO)));
        }
        List<RefundMethod> allowed = sameDay
                ? List.of(RefundMethod.CASH, RefundMethod.ORIGINAL_METHOD)
                : List.of(RefundMethod.CREDIT_NOTE);
        return new ReturnableView(sale.getId(), sale.getReceiptNumber(), sale.getCustomerId(), customerName,
                sale.getPaidAt(), sameDay, allowed, methods, lines, sale.getBranchId(), branchName, foreign,
                sale.getOrderType(), sale.isExclusive());
    }

    /** One payment method of the sale: what was paid, what was refunded by earlier returns, and what is left. */
    public record PaymentRefund(String paymentMethodCode, String paymentMethodName, BigDecimal paid,
                                BigDecimal refunded, BigDecimal refundable) {
    }

    /**
     * One line of the sale: the quantities, the amount refunded so far, and what a return line copies from the line
     * (sku, price, tax). {@code returnableQuantity} is the cap after the claims of other returns.
     */
    public record Line(Long saleLineId, Long productId, String productName, String sku, BigDecimal quantity,
                       BigDecimal returnedQuantity, BigDecimal returnableQuantity, BigDecimal netTotal,
                       BigDecimal refundedAmount, BigDecimal unitPrice, BigDecimal unitCost, String taxCode,
                       BigDecimal taxRate, BigDecimal listPrice, Long priceListId, boolean priceOverridden) {

        static Line of(ReturnSourceLine source, BigDecimal refunded) {
            return new Line(source.id(), source.productId(), source.description(), source.sku(), source.quantity(),
                    source.returnedQuantity(), source.returnableQuantity(), source.total(), refunded,
                    source.unitPrice(), source.unitCost(), source.taxCode(), source.taxRate(), source.listPrice(),
                    source.priceListId(), source.priceOverridden());
        }
    }
}
