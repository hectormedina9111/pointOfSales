package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.sales.domain.DiscountType;

/** Input of the sales operations, already validated by the web layer. */
public final class SaleData {

    private SaleData() {
    }

    /** One line: a product, its quantity and an optional discount on the line. */
    public record Line(long productId, BigDecimal quantity, DiscountType discountType, BigDecimal discountValue) {
    }

    /** A sale to quote or to hold: lines and an optional discount on the whole sale. */
    public record Draft(List<Line> lines, DiscountType discountType, BigDecimal discountValue, String notes) {
    }

    /** One payment: the method by its code, the amount applied, and what the card or the cash needs. */
    public record PaymentLine(String paymentMethodCode, BigDecimal amount, BigDecimal amountReceived, String reference,
                              String cardLast4) {
    }

    /** Credentials of the manager who authorizes a discount. The password is never printed. */
    public record Authorization(String username, String password) {

        @Override
        public String toString() {
            return "Authorization[username=" + username + ", password=***]";
        }
    }

    /**
     * Charge of a sale. {@code holdId} resumes a sale on hold, which then becomes this sale; the idempotency key makes
     * a repeated charge return the same sale.
     */
    public record Checkout(Long holdId, List<Line> lines, DiscountType discountType, BigDecimal discountValue,
                           List<PaymentLine> payments, String idempotencyKey, Authorization authorization,
                           String notes) {
    }
}
