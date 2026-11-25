package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.SerialNumbers;

/**
 * Computes what a return refunds. It is pure: it receives the lines of the original sale (their quantities, totals and
 * the quantity each one can still take), the lines and quantities asked for, and the amounts refunded before for each
 * original line. The original sale may be of this node or of another branch.
 *
 * <p>For a returned quantity {@code q} of a line with quantity {@code Q} and net total {@code N} (after the line discount
 * and its share of the sale discount, tax included): {@code amount = round(N * q / Q)}, half up. When {@code q} is all
 * the ledger has left of the line ({@code Q - returned}), {@code amount = N - refunded before}, so returning everything
 * refunds exactly what was paid. The tax is split backwards from that amount with the rate of the original line.
 */
public final class ReturnCalculator {

    private static final BigDecimal FIFTEEN = new BigDecimal("15");
    private static final BigDecimal EIGHTEEN = new BigDecimal("18");

    private ReturnCalculator() {
    }

    /**
     * @param returnable    true when the original sale is a paid sale or partly returned
     * @param source        the lines of the original sale
     * @param requested     the lines and quantities asked for; at least one, each original line once
     * @param refundedByLine the amount already refunded per original line id, by earlier returns
     * @throws BusinessRuleException RETURN_NOT_ALLOWED, RETURN_LINES_REQUIRED, DUPLICATE_RETURN_LINE,
     *                               RETURN_LINE_NOT_FOUND, INVALID_QUANTITY, RETURN_QUANTITY_EXCEEDED or RETURN_TOTAL_ZERO
     */
    public static ReturnTotals calculate(boolean returnable, List<ReturnSourceLine> source,
                                         List<ReturnLineRequest> requested, Map<Long, BigDecimal> refundedByLine) {
        if (!returnable) {
            throw new BusinessRuleException("RETURN_NOT_ALLOWED", "Only a paid sale can be returned");
        }
        if (requested == null || requested.isEmpty()) {
            throw new BusinessRuleException("RETURN_LINES_REQUIRED", "The return needs at least one line");
        }
        Map<Long, ReturnSourceLine> lines = new HashMap<>();
        for (ReturnSourceLine line : source) {
            lines.put(line.id(), line);
        }

        Set<Long> seen = new HashSet<>();
        List<CalculatedReturnLine> calculated = new ArrayList<>(requested.size());
        for (ReturnLineRequest request : requested) {
            if (!seen.add(request.saleLineId())) {
                throw new BusinessRuleException("DUPLICATE_RETURN_LINE", "A line is returned twice");
            }
            ReturnSourceLine line = lines.get(request.saleLineId());
            if (line == null) {
                throw new BusinessRuleException("RETURN_LINE_NOT_FOUND", "The line is not part of the sale");
            }
            if (request.quantity() == null) {
                throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
            }
            BigDecimal quantity = request.quantity().setScale(3, RoundingMode.HALF_UP);
            if (quantity.signum() <= 0) {
                throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
            }
            if (quantity.compareTo(line.returnableQuantity()) > 0) {
                throw new BusinessRuleException("RETURN_QUANTITY_EXCEEDED",
                        "The quantity is more than the quantity left to return");
            }
            List<String> serials = serialsOf(line, request, quantity);
            BigDecimal refunded = refundedByLine.getOrDefault(line.id(), Money.ZERO);
            BigDecimal amount = amountOf(line, quantity, refunded);
            TaxSplit split = TaxSplit.of(amount, line.taxRate());
            calculated.add(new CalculatedReturnLine(line, quantity, split.taxableBase(), split.taxAmount(), amount,
                    serials));
        }
        return totalsOf(calculated);
    }

    /**
     * The serial numbers that a return line brings back. A product that tracks serials needs one per unit, each one
     * still returnable in the original line; a product without serials takes none.
     */
    private static List<String> serialsOf(ReturnSourceLine line, ReturnLineRequest request, BigDecimal quantity) {
        if (!line.tracksSerials()) {
            return List.of();
        }
        List<String> serials = SerialNumbers.normalize(request.serialNumbers());
        SerialNumbers.requireOnePerUnit(quantity, serials);
        for (String number : serials) {
            if (!line.returnableSerialNumbers().contains(number)) {
                throw new BusinessRuleException("SERIAL_NOT_FOUND",
                        "The serial number was not sold in this line or was already returned", number);
            }
        }
        return serials;
    }

    private static BigDecimal amountOf(ReturnSourceLine line, BigDecimal quantity, BigDecimal refunded) {
        if (quantity.compareTo(line.ledgerRemaining()) == 0) {
            return Money.scale2(line.total().subtract(refunded));
        }
        return line.total().multiply(quantity).divide(line.quantity(), 2, RoundingMode.HALF_UP);
    }

    private static ReturnTotals totalsOf(List<CalculatedReturnLine> lines) {
        BigDecimal subtotal = Money.ZERO;
        BigDecimal exempt = Money.ZERO;
        BigDecimal taxable15 = Money.ZERO;
        BigDecimal taxable18 = Money.ZERO;
        BigDecimal otherTaxable = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal total = Money.ZERO;
        for (CalculatedReturnLine line : lines) {
            BigDecimal rate = line.original().taxRate();
            subtotal = subtotal.add(line.subtotal());
            taxTotal = taxTotal.add(line.taxAmount());
            total = total.add(line.total());
            if (rate.signum() == 0) {
                exempt = exempt.add(line.subtotal());
            } else if (rate.compareTo(FIFTEEN) == 0) {
                taxable15 = taxable15.add(line.subtotal());
            } else if (rate.compareTo(EIGHTEEN) == 0) {
                taxable18 = taxable18.add(line.subtotal());
            } else {
                otherTaxable = otherTaxable.add(line.subtotal());
            }
        }
        if (total.signum() == 0) {
            throw new BusinessRuleException("RETURN_TOTAL_ZERO", "A return must refund an amount greater than zero");
        }
        return new ReturnTotals(lines, subtotal, exempt, taxable15, taxable18, otherTaxable, taxTotal, total);
    }
}
