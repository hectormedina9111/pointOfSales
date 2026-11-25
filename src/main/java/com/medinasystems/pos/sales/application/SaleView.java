package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;

/** A sale with its lines and payments. Costs are not shown: they belong to the reports. */
public record SaleView(Long id, Long internalNumber, String receiptNumber, String type, String documentType,
                       SaleStatus status, Long branchId, Long cashRegisterId, Long cashSessionId, Long cashierId,
                       boolean exclusive, SaleTotalsView totals, BigDecimal amountPaid, BigDecimal changeDue,
                       DiscountType discountType, BigDecimal discountValue, Long discountAuthorizedBy, String notes,
                       String fiscalStatus, Instant paidAt, Instant syncedAt, Instant createdAt,
                       List<SaleLineView> lines, List<PaymentView> payments) {

    public static SaleView from(Sale sale) {
        return new SaleView(sale.getId(), sale.getInternalNumber(), sale.getReceiptNumber(), sale.getType(),
                sale.getDocumentType(), sale.getStatus(), sale.getBranchId(), sale.getCashRegisterId(),
                sale.getCashSessionId(), sale.getCashierId(), sale.isExclusive(), SaleTotalsView.from(sale),
                sale.getAmountPaid(), sale.getChangeDue(), sale.getDiscountType(), sale.getDiscountValue(),
                sale.getDiscountAuthorizedBy(), sale.getNotes(), sale.getFiscalStatus(), sale.getPaidAt(),
                sale.getSyncedAt(), sale.getCreatedAt(), sale.getLines().stream().map(line -> SaleLineView.from(line))
                        .toList(),
                sale.getPayments().stream().map(payment -> PaymentView.from(payment)).toList());
    }
}
