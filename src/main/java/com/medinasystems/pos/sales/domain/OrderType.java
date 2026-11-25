package com.medinasystems.pos.sales.domain;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * How the order is paid. CASH is paid at the register. CREDIT is paid later on the customer's account; the credit module
 * comes in phase 7, so in 3A a CREDIT order is refused.
 */
public enum OrderType {
    CASH,
    CREDIT;

    /**
     * The type a request asks for: CASH when it names none.
     *
     * @throws BusinessRuleException CREDIT_NOT_AVAILABLE for CREDIT
     */
    public static OrderType requested(OrderType requested) {
        OrderType type = requested == null ? CASH : requested;
        if (type == CREDIT) {
            throw new BusinessRuleException("CREDIT_NOT_AVAILABLE", "Credit orders are not available yet");
        }
        return type;
    }
}
