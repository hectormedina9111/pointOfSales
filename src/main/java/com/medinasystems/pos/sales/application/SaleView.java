package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;

/**
 * A sale with its customer, its lines and its payments. The customer fields are copied from the customer as it is now.
 * Costs are not shown: they belong to the reports.
 */
public record SaleView(Long id, Long internalNumber, String receiptNumber, String type, String documentType,
                       SaleStatus status, Long branchId, Long cashRegisterId, Long cashSessionId, Long cashierId,
                       Long customerId, String customerName, String customerDocumentType,
                       String customerDocumentNumber, OrderType orderType, boolean exclusive, SaleTotalsView totals,
                       BigDecimal amountPaid, BigDecimal changeDue, DiscountType discountType,
                       BigDecimal discountValue, Long discountAuthorizedBy, Long priceAuthorizedBy, String notes,
                       String fiscalStatus, Instant paidAt, Instant syncedAt, Instant createdAt,
                       List<SaleLineView> lines, List<PaymentView> payments) {

    public static SaleView from(Sale sale, CustomerInfo customer) {
        Objects.requireNonNull(customer, "the customer of the sale is required");
        return new SaleView(sale.getId(), sale.getInternalNumber(), sale.getReceiptNumber(), sale.getType(),
                sale.getDocumentType(), sale.getStatus(), sale.getBranchId(), sale.getCashRegisterId(),
                sale.getCashSessionId(), sale.getCashierId(), sale.getCustomerId(), customer.displayName(),
                customer.documentType(), customer.documentNumber(), sale.getOrderType(), sale.isExclusive(),
                SaleTotalsView.from(sale), sale.getAmountPaid(), sale.getChangeDue(), sale.getDiscountType(),
                sale.getDiscountValue(), sale.getDiscountAuthorizedBy(), sale.getPriceAuthorizedBy(), sale.getNotes(),
                sale.getFiscalStatus(), sale.getPaidAt(), sale.getSyncedAt(), sale.getCreatedAt(),
                sale.getLines().stream().map(line -> SaleLineView.from(line)).toList(),
                sale.getPayments().stream().map(payment -> PaymentView.from(payment)).toList());
    }
}
