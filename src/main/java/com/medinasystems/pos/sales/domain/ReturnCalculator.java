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

/**
 * Computes what a return refunds. It is pure: it receives the original sale, the lines and quantities asked for, and the
 * amounts refunded before for each original line.
 *
 * <p>For a returned quantity {@code q} of a line with quantity {@code Q} and net total {@code N} (after the line discount
 * and its share of the sale discount, tax included): {@code amount = round(N * q / Q)}, half up. When {@code q} is all
 * that is left of the line, {@code amount = N - refunded before}, so returning everything refunds exactly what was paid.
 * The tax is split backwards from that amount with the rate of the original line.
 */
public final class ReturnCalculator {

    private static final BigDecimal FIFTEEN = new BigDecimal("15");
    private static final BigDecimal EIGHTEEN = new BigDecimal("18");

    private ReturnCalculator() {
    }

    /**
     * @param original       the sale being returned, with its lines and the quantities already returned
     * @param requested      the lines and quantities asked for; at least one, each original line once
     * @param refundedByLine the amount already refunded per original line id, by earlier returns
     * @throws BusinessRuleException RETURN_NOT_ALLOWED, RETURN_LINES_REQUIRED, DUPLICATE_RETURN_LINE,
     *                               RETURN_LINE_NOT_FOUND, INVALID_QUANTITY, RETURN_QUANTITY_EXCEEDED or RETURN_TOTAL_ZERO
     */
    public static ReturnTotals calculate(Sale original, List<ReturnLineRequest> requested,
                                         Map<Long, BigDecimal> refundedByLine) {
        if (!original.isReturnable()) {
            throw new BusinessRuleException("RETURN_NOT_ALLOWED", "Only a paid sale can be returned");
        }
        if (requested == null || requested.isEmpty()) {
            throw new BusinessRuleException("RETURN_LINES_REQUIRED", "The return needs at least one line");
        }
        Map<Long, SaleLine> lines = new HashMap<>();
        for (SaleLine line : original.getLines()) {
            lines.put(line.getId(), line);
        }

        Set<Long> seen = new HashSet<>();
        List<CalculatedReturnLine> calculated = new ArrayList<>(requested.size());
        for (ReturnLineRequest request : requested) {
            if (!seen.add(request.saleLineId())) {
                throw new BusinessRuleException("DUPLICATE_RETURN_LINE", "A line is returned twice");
            }
            SaleLine line = lines.get(request.saleLineId());
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
            BigDecimal remaining = line.getQuantity().subtract(line.getReturnedQuantity());
            if (quantity.compareTo(remaining) > 0) {
                throw new BusinessRuleException("RETURN_QUANTITY_EXCEEDED",
                        "The quantity is more than the quantity left to return");
            }
            BigDecimal refunded = refundedByLine.getOrDefault(line.getId(), Money.ZERO);
            BigDecimal amount = amountOf(line, quantity, remaining, refunded);
            TaxSplit split = TaxSplit.of(amount, line.getTaxRate());
            calculated.add(new CalculatedReturnLine(line, quantity, split.taxableBase(), split.taxAmount(), amount));
        }
        return totalsOf(calculated);
    }

    private static BigDecimal amountOf(SaleLine line, BigDecimal quantity, BigDecimal remaining,
                                       BigDecimal refunded) {
        if (quantity.compareTo(remaining) == 0) {
            return Money.scale2(line.getTotal().subtract(refunded));
        }
        return line.getTotal().multiply(quantity).divide(line.getQuantity(), 2, RoundingMode.HALF_UP);
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
            BigDecimal rate = line.original().getTaxRate();
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
