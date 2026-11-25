package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.medinasystems.pos.cash.CashRegisterInfo;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.security.UserInfo;

/**
 * The data of a printed 80 mm ticket. The date and time are local to the company's time zone, which is named in
 * {@code timeZone}. The printing itself happens in the browser.
 */
public record TicketView(TicketCompany company, TicketBranch branch, String cashRegisterName, String receiptNumber,
                         LocalDate date, LocalTime time, String timeZone, String cashierName, List<TicketLine> lines,
                         SaleTotalsView totals, List<PaymentView> payments, BigDecimal changeDue) {

    public record TicketCompany(String legalName, String tradeName, String taxId) {
    }

    public record TicketBranch(String name, String address) {
    }

    /** A ticket line: the description, the quantity, the unit price, the discount and the total with tax. */
    public record TicketLine(String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discount,
                             BigDecimal total) {
    }

    public static TicketView of(Sale sale, CompanyInfo company, BranchInfo branch, CashRegisterInfo register,
                                UserInfo cashier, ZoneId zone) {
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
                sale.getLines().stream().map(line -> new TicketLine(line.getDescription(), line.getQuantity(),
                        line.getUnitPrice(), line.getDiscountAmount(), line.getTotal())).toList(),
                SaleTotalsView.from(sale),
                sale.getPayments().stream().map(payment -> PaymentView.from(payment)).toList(),
                sale.getChangeDue());
    }
}
