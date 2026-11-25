package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.medinasystems.pos.cash.CashRegisterInfo;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.RefundMethod;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.security.UserInfo;

/**
 * The data of a printed 80 mm ticket, or of the return voucher (type RETURN) with its credit note when one was issued. The date and time are local to the company's time zone, which is named in
 * {@code timeZone}. The printing itself happens in the browser.
 */
public record TicketView(TicketCompany company, TicketBranch branch, String cashRegisterName, String receiptNumber,
                         LocalDate date, LocalTime time, String timeZone, String cashierName, Long customerId,
                         String customerName, String customerDocumentType, String customerDocumentNumber,
                         OrderType orderType, List<TicketLine> lines, SaleTotalsView totals,
                         List<PaymentView> payments, BigDecimal changeDue, SaleType type,
                         String originalReceiptNumber, String reason, RefundMethod refundMethod,
                         String creditNoteNumber, BigDecimal creditNoteAmount) {

    public record TicketCompany(String legalName, String tradeName, String taxId) {
    }

    public record TicketBranch(String name, String address) {
    }

    /** A ticket line: the description, the quantity, the unit price, the discount and the total with tax. */
    public record TicketLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount,
                             BigDecimal total) {
    }

    public static TicketView of(Sale sale, CompanyInfo company, BranchInfo branch, CashRegisterInfo register,
                                UserInfo cashier, CustomerInfo customer, CreditNote creditNote, ZoneId zone) {
        var local = sale.getPaidAt().atZone(zone);
        return new TicketView(
                new TicketCompany(company.legalName(), company.tradeName(), company.taxId()),
                new TicketBranch(branch.name(), branch.address()),
                register.name(),
                sale.getReceiptNumber(),
                local.toLocalDate(),
                local.toLocalTime().truncatedTo(ChronoUnit.SECONDS),
                zone.getId(),
                cashier.fullName(),
                customer.id(),
                customer.displayName(),
                customer.documentType(),
                customer.documentNumber(),
                sale.getOrderType(),
                sale.getLines().stream().map(line -> new TicketLine(line.getDescription(), line.getQuantity(),
                        line.getUnitPrice(), line.getDiscountAmount(), line.getTotal())).toList(),
                SaleTotalsView.from(sale),
                sale.getPayments().stream().map(payment -> PaymentView.from(payment)).toList(),
                sale.getChangeDue(),
                sale.getType(),
                sale.getOriginalReceiptNumber(),
                sale.getReason(),
                sale.getRefundMethod(),
                creditNote == null ? null : creditNote.getNumber(),
                creditNote == null ? null : creditNote.getAmount());
    }
}
