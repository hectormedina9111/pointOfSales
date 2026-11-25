package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;

/** A sale as listed: without lines and payments. {@code branchId} shows which store the sale came from. */
public record SaleSummaryView(Long id, Long internalNumber, String receiptNumber, SaleStatus status, Long branchId,
                              Long cashRegisterId, Long cashSessionId, Long cashierId, BigDecimal total,
                              Instant paidAt, Instant createdAt) {

    public static SaleSummaryView from(Sale sale) {
        return new SaleSummaryView(sale.getId(), sale.getInternalNumber(), sale.getReceiptNumber(),
                sale.getStatus(), sale.getBranchId(), sale.getCashRegisterId(), sale.getCashSessionId(),
                sale.getCashierId(), sale.getTotal(), sale.getPaidAt(), sale.getCreatedAt());
    }
}
