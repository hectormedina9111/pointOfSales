package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Objects;

import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.RefundMethod;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;

/**
 * A sale or a return with its customer, its lines and its payments. The customer fields are copied from the customer as it
 * is now. A SALE lists its returns; a RETURN carries its own credit note when one was issued. Costs are not shown.
 */
public record SaleView(Long id, Long internalNumber, String receiptNumber, SaleType type, String documentType,
                       SaleStatus status, Long branchId, Long cashRegisterId, Long cashSessionId, Long cashierId,
                       Long customerId, String customerName, String customerDocumentType,
                       String customerDocumentNumber, OrderType orderType, boolean exclusive, SaleTotalsView totals,
                       BigDecimal amountPaid, BigDecimal changeDue, DiscountType discountType,
                       BigDecimal discountValue, Long discountAuthorizedBy, Long priceAuthorizedBy, String notes,
                       String fiscalStatus, Instant paidAt, Instant syncedAt, Instant createdAt,
                       Long originalSaleId, String originalReceiptNumber, String reason, RefundMethod refundMethod,
                       List<ReturnRef> returns, CreditNoteRef creditNote,
                       List<SaleLineView> lines, List<PaymentView> payments) {

    /** A return of this sale, as listed under the sale. */
    public record ReturnRef(Long id, String receiptNumber, BigDecimal total, Instant createdAt) {
    }

    /** The credit note issued by a return: its number, its amount and what is left of it. */
    public record CreditNoteRef(Long id, String number, BigDecimal amount, BigDecimal balance) {
    }

    public static SaleView from(Sale sale, CustomerInfo customer, List<ReturnRef> returns, CreditNoteRef creditNote,
                                Map<Long, String> creditNoteNumbers, Function<Long, String> serialModes) {
        Objects.requireNonNull(customer, "the customer of the sale is required");
        return new SaleView(sale.getId(), sale.getInternalNumber(), sale.getReceiptNumber(), sale.getType(),
                sale.getDocumentType(), sale.getStatus(), sale.getBranchId(), sale.getCashRegisterId(),
                sale.getCashSessionId(), sale.getCashierId(), sale.getCustomerId(), customer.displayName(),
                customer.documentType(), customer.documentNumber(), sale.getOrderType(), sale.isExclusive(),
                SaleTotalsView.from(sale), sale.getAmountPaid(), sale.getChangeDue(), sale.getDiscountType(),
                sale.getDiscountValue(), sale.getDiscountAuthorizedBy(), sale.getPriceAuthorizedBy(), sale.getNotes(),
                sale.getFiscalStatus(), sale.getPaidAt(), sale.getSyncedAt(), sale.getCreatedAt(),
                sale.getOriginalSaleId(), sale.getOriginalReceiptNumber(), sale.getReason(), sale.getRefundMethod(),
                returns, creditNote,
                sale.getLines().stream().map(line -> SaleLineView.from(line, serialModes)).toList(),
                sale.getPayments().stream().map(payment -> PaymentView.from(payment, creditNoteNumbers)).toList());
    }
}
