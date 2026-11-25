package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.Payment;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleLine;

/**
 * A paid sale or return as the store uploads it (event SALE_PAID): the sale row, its lines and its payments, with the
 * store's own ids. A return also carries the credit note it issued, when there is one. The central node stores the same
 * rows, so ids never change between nodes. Field names are part of the protocol; the central node reads them in
 * {@link SaleUploadHandler}. Money is sent as JSON numbers, timestamps as ISO-8601 text.
 */
public record SaleDocument(long id, long internalNumber, String receiptNumber, String type, String documentType,
                           String status, long branchId, long cashRegisterId, long cashSessionId, long cashierId,
                           long customerId, String orderType, boolean exclusive, BigDecimal subtotal,
                           BigDecimal discountTotal, BigDecimal exemptAmount,
                           BigDecimal taxableAmount15, BigDecimal taxableAmount18, BigDecimal taxTotal,
                           BigDecimal total, BigDecimal amountPaid, BigDecimal changeDue, String paidAt,
                           String discountType, BigDecimal discountValue, Long discountAuthorizedBy,
                           Long priceAuthorizedBy, String notes,
                           String idempotencyKey, String fiscalStatus, String createdAt, String createdBy,
                           String updatedAt, String updatedBy, Integer version, List<LineDocument> lines,
                           List<PaymentDocument> payments, Long originalSaleId, String originalReceiptNumber,
                           String reason, String refundMethod, Long returnAuthorizedBy,
                           CreditNoteDocument creditNote) {

    public static final String EVENT_TYPE = "SALE_PAID";

    public static SaleDocument of(Sale sale) {
        return of(sale, null);
    }

    /** The document of a sale, or of a return together with the credit note it issued (null when none). */
    public static SaleDocument of(Sale sale, CreditNote creditNote) {
        return new SaleDocument(sale.getId(), sale.getInternalNumber(), sale.getReceiptNumber(),
                name(sale.getType()), sale.getDocumentType(), sale.getStatus().name(), sale.getBranchId(),
                sale.getCashRegisterId(), sale.getCashSessionId(), sale.getCashierId(), sale.getCustomerId(),
                name(sale.getOrderType()), sale.isExclusive(), sale.getSubtotal(), sale.getDiscountTotal(),
                sale.getExemptAmount(), sale.getTaxableAmount15(), sale.getTaxableAmount18(), sale.getTaxTotal(),
                sale.getTotal(), sale.getAmountPaid(), sale.getChangeDue(), text(sale.getPaidAt()),
                name(sale.getDiscountType()), sale.getDiscountValue(), sale.getDiscountAuthorizedBy(),
                sale.getPriceAuthorizedBy(), sale.getNotes(), sale.getIdempotencyKey(), sale.getFiscalStatus(),
                text(sale.getCreatedAt()), sale.getCreatedBy(), text(sale.getUpdatedAt()), sale.getUpdatedBy(),
                sale.getVersion(),
                sale.getLines().stream().map(LineDocument::of).toList(),
                sale.getPayments().stream().map(PaymentDocument::of).toList(),
                sale.getOriginalSaleId(), sale.getOriginalReceiptNumber(), sale.getReason(),
                name(sale.getRefundMethod()), sale.getReturnAuthorizedBy(),
                creditNote == null ? null : CreditNoteDocument.of(creditNote));
    }

    /** One line of the sale, with the copy of the product as it was sold. */
    public record LineDocument(long id, int lineNumber, long productId, String sku, String description,
                               BigDecimal quantity, BigDecimal unitPrice, BigDecimal listPrice,
                               boolean priceOverridden, BigDecimal unitCost, String discountType,
                               BigDecimal discountValue, BigDecimal discountAmount, String taxCode,
                               BigDecimal taxRate, BigDecimal taxAmount, BigDecimal subtotal, BigDecimal total,
                               Long priceListId, Long originalLineId, String createdAt, String createdBy,
                               String updatedAt, String updatedBy, Integer version) {

        static LineDocument of(SaleLine line) {
            return new LineDocument(line.getId(), line.getLineNumber(), line.getProductId(), line.getSku(),
                    line.getDescription(), line.getQuantity(), line.getUnitPrice(), line.getListPrice(),
                    line.isPriceOverridden(), line.getUnitCost(),
                    name(line.getDiscountType()), line.getDiscountValue(), line.getDiscountAmount(),
                    line.getTaxCode(), line.getTaxRate(), line.getTaxAmount(), line.getSubtotal(), line.getTotal(),
                    line.getPriceListId(), line.getOriginalLineId(), text(line.getCreatedAt()), line.getCreatedBy(),
                    text(line.getUpdatedAt()), line.getUpdatedBy(), line.getVersion());
        }
    }

    /** One payment of the sale. The card number is never here: only its last four digits. */
    public record PaymentDocument(long id, long paymentMethodId, BigDecimal amount, BigDecimal amountReceived,
                                  String reference, String cardLast4, String paidAt, String createdAt,
                                  String createdBy, String updatedAt, String updatedBy, Integer version,
                                  Long creditNoteId, String redemptionKey) {

        static PaymentDocument of(Payment payment) {
            return new PaymentDocument(payment.getId(), payment.getPaymentMethod().getId(), payment.getAmount(),
                    payment.getAmountReceived(), payment.getReference(), payment.getCardLast4(),
                    text(payment.getPaidAt()), text(payment.getCreatedAt()), payment.getCreatedBy(),
                    text(payment.getUpdatedAt()), payment.getUpdatedBy(), payment.getVersion(),
                    payment.getCreditNoteId(), payment.getRedemptionKey());
        }
    }

    /** The credit note issued by a return. Its balance is set by the central node when it stores the note. */
    public record CreditNoteDocument(long id, String number, long customerId, long returnSaleId, long branchId,
                                     BigDecimal amount, BigDecimal balance, String status, String issuedAt,
                                     String createdAt, String createdBy, String updatedAt, String updatedBy,
                                     Integer version) {

        static CreditNoteDocument of(CreditNote note) {
            return new CreditNoteDocument(note.getId(), note.getNumber(), note.getCustomerId(),
                    note.getReturnSaleId(), note.getBranchId(), note.getAmount(), note.getBalance(),
                    note.getStatus().name(), text(note.getIssuedAt()), text(note.getCreatedAt()),
                    note.getCreatedBy(), text(note.getUpdatedAt()), note.getUpdatedBy(), note.getVersion());
        }
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
