package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.SaleStatus;

/**
 * Filters of the sales list. A null filter does not restrict the result. {@code branchId} and {@code cashierId} are
 * replaced by the visibility rules of the node through {@link #withScope}; the other fields come from the request.
 * {@code search} is trimmed, and a blank one means no search.
 */
public record SaleFilter(Long branchId, Long cashierId, SaleStatus status, OrderType orderType, Long cashSessionId,
                         Long cashRegisterId, Long customerId, String search, BigDecimal minTotal,
                         BigDecimal maxTotal, Instant from, Instant to) {

    public SaleFilter {
        search = search == null || search.isBlank() ? null : search.strip();
    }

    /** The same filter with the branch and the cashier that the visibility rules allow. */
    public SaleFilter withScope(Long scopedBranchId, Long scopedCashierId) {
        return new SaleFilter(scopedBranchId, scopedCashierId, status, orderType, cashSessionId, cashRegisterId,
                customerId, search, minTotal, maxTotal, from, to);
    }
}
