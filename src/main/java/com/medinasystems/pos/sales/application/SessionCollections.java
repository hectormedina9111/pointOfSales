package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.SessionPaymentTotals;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.sales.infrastructure.PaymentRepository;

/**
 * Implements the cash module's extension point: what a cash session collected, per payment method. Sales that are paid or
 * partly returned count; the refunds of returns count negative. Cash is already net of change.
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
        List<SaleStatus> statuses = List.of(SaleStatus.PAID, SaleStatus.PARTIALLY_RETURNED, SaleStatus.RETURNED);
        for (Object[] row : payments.sumBySessionAndStatuses(sessionId, statuses, SaleType.RETURN)) {
            collected.put((String) row[0], (BigDecimal) row[1]);
        }
        return collected;
    }
}
