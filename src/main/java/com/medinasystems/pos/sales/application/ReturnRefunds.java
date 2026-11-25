package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.sales.infrastructure.PaymentRepository;
import com.medinasystems.pos.sales.infrastructure.SaleLineRepository;

/** The amounts that the returns of a sale have already refunded, read from the database. Shared by the stores and the central node. */
final class ReturnRefunds {

    private ReturnRefunds() {
    }

    /** The amount refunded per original line id, by the returns of the sale. */
    static Map<Long, BigDecimal> byLine(SaleLineRepository lines, long saleId) {
        Map<Long, BigDecimal> refunded = new HashMap<>();
        for (Object[] row : lines.sumTotalsByOriginalLine(saleId, SaleType.RETURN)) {
            refunded.put((Long) row[0], (BigDecimal) row[1]);
        }
        return refunded;
    }

    /** The amount refunded per payment method code, by the returns of the sale. */
    static Map<String, BigDecimal> byMethod(PaymentRepository payments, long saleId) {
        Map<String, BigDecimal> refunded = new HashMap<>();
        for (Object[] row : payments.sumByMethodOfReturns(saleId, SaleType.RETURN)) {
            refunded.put((String) row[0], (BigDecimal) row[1]);
        }
        return refunded;
    }
}
