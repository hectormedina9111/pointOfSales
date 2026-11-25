package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;

/**
 * A sale or a return as listed: without lines and payments. {@code branchId} shows which store the sale came from; the
 * customer fields show who bought.
 */
public record SaleSummaryView(Long id, Long internalNumber, String receiptNumber, SaleType type, SaleStatus status,
                              Long branchId, Long cashRegisterId, Long cashSessionId, Long cashierId, Long customerId,
                              String customerName, String customerDocumentType, String customerDocumentNumber,
                              OrderType orderType, BigDecimal total, Instant paidAt, Instant createdAt) {

    public static SaleSummaryView from(Sale sale, CustomerInfo customer) {
        Objects.requireNonNull(customer, "the customer of the sale is required");
        return new SaleSummaryView(sale.getId(), sale.getInternalNumber(), sale.getReceiptNumber(), sale.getType(),
                sale.getStatus(), sale.getBranchId(), sale.getCashRegisterId(), sale.getCashSessionId(),
                sale.getCashierId(), sale.getCustomerId(), customer.displayName(), customer.documentType(),
                customer.documentNumber(), sale.getOrderType(), sale.getTotal(), sale.getPaidAt(), sale.getCreatedAt());
    }
}
