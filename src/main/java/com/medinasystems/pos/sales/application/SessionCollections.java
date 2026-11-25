package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.SessionPaymentTotals;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.infrastructure.PaymentRepository;

/**
 * Implements the cash module's extension point: what a cash session collected, per payment method. Only paid sales
 * count. Cash is already net of change, because a payment records the amount applied to the sale.
 */
@Component
public class SessionCollections implements SessionPaymentTotals {

    private final PaymentRepository payments;

    public SessionCollections(PaymentRepository payments) {
        this.payments = payments;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> collectedByMethod(long sessionId) {
        Map<String, BigDecimal> collected = new LinkedHashMap<>();
        for (Object[] row : payments.sumBySessionAndStatus(sessionId, SaleStatus.PAID)) {
            collected.put((String) row[0], (BigDecimal) row[1]);
        }
        return collected;
    }
}
