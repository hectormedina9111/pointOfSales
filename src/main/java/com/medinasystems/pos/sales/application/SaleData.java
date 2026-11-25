package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.OrderType;

/** Input of the sales operations, already validated by the web layer. */
public final class SaleData {

    private SaleData() {
    }

    /**
     * One line: a product, its quantity, an optional discount on the line and an optional unit price. A null unit price
     * means the list price; a different one is a price change, which needs an authorization.
     */
    public record Line(long productId, BigDecimal quantity, DiscountType discountType, BigDecimal discountValue,
                       BigDecimal unitPrice) {
    }

    /**
     * A sale to quote or to hold. A null customer is the final consumer; a null order type is a cash order. The
     * authorization is used when a discount or a price change needs a manager.
     */
    public record Draft(List<Line> lines, DiscountType discountType, BigDecimal discountValue, Long customerId,
                        OrderType orderType, Authorization authorization, String notes) {
    }

    /** One payment: the method by its code, the amount applied, and what the card or the cash needs. */
    public record PaymentLine(String paymentMethodCode, BigDecimal amount, BigDecimal amountReceived, String reference,
                              String cardLast4) {
    }

    /** Credentials of the manager who authorizes a discount or a price change. The password is never printed. */
    public record Authorization(String username, String password) {

        @Override
        public String toString() {
            return "Authorization[username=" + username + ", password=***]";
        }
    }

    /**
     * Direct charge of a sale. {@code holdId} resumes a sale on hold, which then becomes this sale; the idempotency key
     * makes a repeated charge return the same sale.
     */
    public record Checkout(Long holdId, List<Line> lines, DiscountType discountType, BigDecimal discountValue,
                           Long customerId, OrderType orderType, List<PaymentLine> payments, String idempotencyKey,
                           Authorization authorization, String notes) {
    }

    /**
     * Charge of a sale on hold, with the lines and prices it was saved with. Nothing is priced again, so no
     * authorization is needed. {@code notes}, when given, replaces the notes of the sale.
     */
    public record HoldCheckout(long holdId, List<PaymentLine> payments, String idempotencyKey, String notes) {
    }

    /** One line of a return: a line of the original sale and the quantity that comes back. */
    public record ReturnLine(long saleLineId, BigDecimal quantity) {
    }

    /**
     * A return of a sale. {@code refunds} are the refunds of a return on the day of the sale; they must be empty for a
     * later return, which is refunded with a credit note. The authorization is used when the signed-in user does not hold
     * the return privilege.
     */
    public record Return(long saleId, List<ReturnLine> lines, String reason, List<PaymentLine> refunds,
                         Authorization authorization, String idempotencyKey) {
    }
}
