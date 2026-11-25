package com.medinasystems.pos.cash;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Extension point for the sales module. A cash session's expected amounts include what was collected in it, and sales
 * knows that; this interface lets the cash module ask without depending on sales. Until sales implements it, the
 * session has no collections.
 */
public interface SessionPaymentTotals {

    /**
     * Amount collected in the session per payment method code (CASH, CARD, TRANSFER...). For CASH the amount is net of
     * change: the cash received minus the change given back.
     */
    Map<String, BigDecimal> collectedByMethod(long sessionId);
}
