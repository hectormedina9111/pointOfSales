package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * The totals engine of a sale. It is pure: it receives the priced lines and the discounts, and returns the totals.
 * The server always runs it, so the client's totals are never trusted.
 *
 * <p>Algorithm, per line:
 * <ol>
 *   <li>{@code gross = unitPrice * quantity}, rounded to cents. The price includes tax.</li>
 *   <li>{@code lineDiscount}: the amount, or the percentage of the gross.</li>
 *   <li>{@code afterLine = gross - lineDiscount}.</li>
 * </ol>
 * Then the sale discount, as an amount or a percentage of the sum of the {@code afterLine} values, is shared out
 * proportionally to each line's {@code afterLine}. The last line takes the rounding remainder, so the shares add up
 * to the sale discount exactly. Finally, {@code net = afterLine - share}, and the tax is split backwards from the net:
 * {@code taxableBase = net / (1 + rate / 100)}, {@code tax = net - taxableBase}.
 *
 * <p>The maximum discount percentage is checked on every line (its line discount over its gross) and on the sale
 * (its sale discount over the sum of the {@code afterLine} values). Above the maximum the totals are still computed;
 * {@link SaleTotals#exceedsMaxDiscount()} tells the caller that a manager must authorize them.
 */
public final class SaleCalculator {

    private static final BigDecimal FIFTEEN = new BigDecimal("15");
    private static final BigDecimal EIGHTEEN = new BigDecimal("18");

    private SaleCalculator() {
    }

    /**
     * @param lines                the priced lines; at least one
     * @param saleDiscount         the discount of the whole sale, or null
     * @param maxDiscountPercent   the largest discount a cashier may give without a manager
     * @throws BusinessRuleException LINES_REQUIRED, INVALID_QUANTITY, EXCLUSIVE_PRODUCT_MIXED,
     *                               DISCOUNT_EXCEEDS_LINE or DISCOUNT_EXCEEDS_TOTAL
     */
    public static SaleTotals calculate(List<PricedLine> lines, Discount saleDiscount, BigDecimal maxDiscountPercent) {
        Objects.requireNonNull(maxDiscountPercent, "max discount percent is required");
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("LINES_REQUIRED", "The sale needs at least one line");
        }
        boolean exclusive = requireNoExclusiveMix(lines);

        List<Partial> partials = new ArrayList<>(lines.size());
        BigDecimal sharedBase = Money.ZERO;
        for (PricedLine line : lines) {
            BigDecimal gross = Money.scale2(line.unitPrice().multiply(line.quantity()));
            BigDecimal lineDiscount = lineDiscount(line.discount(), gross);
            BigDecimal afterLine = gross.subtract(lineDiscount);
            partials.add(new Partial(line, gross, lineDiscount, afterLine));
            sharedBase = sharedBase.add(afterLine);
        }

        BigDecimal saleDiscountTotal = saleDiscountTotal(saleDiscount, sharedBase);
        boolean saleExceeds = Money.exceeds(saleDiscountTotal, sharedBase, maxDiscountPercent);

        BigDecimal subtotal = Money.ZERO;
        BigDecimal discountTotal = Money.ZERO;
        BigDecimal exempt = Money.ZERO;
        BigDecimal taxable15 = Money.ZERO;
        BigDecimal taxable18 = Money.ZERO;
        BigDecimal otherTaxable = Money.ZERO;
        BigDecimal taxTotal = Money.ZERO;
        BigDecimal total = Money.ZERO;
        boolean anyLineExceeds = false;
        List<CalculatedLine> calculated = new ArrayList<>(partials.size());

        BigDecimal allocated = Money.ZERO;
        int last = partials.size() - 1;
        for (int i = 0; i <= last; i++) {
            Partial partial = partials.get(i);
            BigDecimal share;
            if (i == last) {
                share = saleDiscountTotal.subtract(allocated);
            } else if (sharedBase.signum() == 0) {
                share = Money.ZERO;
            } else {
                share = saleDiscountTotal.multiply(partial.afterLine()).divide(sharedBase, 2, RoundingMode.HALF_UP);
            }
            allocated = allocated.add(share);

            BigDecimal net = partial.afterLine().subtract(share);
            if (net.signum() < 0) {
                throw new BusinessRuleException("DISCOUNT_EXCEEDS_TOTAL", "The discounts are greater than the sale");
            }
            BigDecimal rate = partial.line().taxRate();
            TaxSplit netSplit = TaxSplit.of(net, rate);
            BigDecimal discountAmount = partial.lineDiscount().add(share);
            boolean lineExceeds = Money.exceeds(partial.lineDiscount(), partial.gross(), maxDiscountPercent);
            anyLineExceeds = anyLineExceeds || lineExceeds;

            calculated.add(new CalculatedLine(i + 1, partial.line(), partial.lineDiscount(), share, discountAmount,
                    netSplit.taxableBase(), netSplit.taxAmount(), net, lineExceeds));

            subtotal = subtotal.add(TaxSplit.of(partial.gross(), rate).taxableBase());
            discountTotal = discountTotal.add(discountAmount);
            taxTotal = taxTotal.add(netSplit.taxAmount());
            total = total.add(net);
            if (rate.signum() == 0) {
                exempt = exempt.add(netSplit.taxableBase());
            } else if (rate.compareTo(FIFTEEN) == 0) {
                taxable15 = taxable15.add(netSplit.taxableBase());
            } else if (rate.compareTo(EIGHTEEN) == 0) {
                taxable18 = taxable18.add(netSplit.taxableBase());
            } else {
                otherTaxable = otherTaxable.add(netSplit.taxableBase());
            }
        }

        return new SaleTotals(calculated, saleDiscount, subtotal, discountTotal, exempt, taxable15, taxable18,
                otherTaxable, taxTotal, total, exclusive, anyLineExceeds || saleExceeds);
    }

    /** An exclusive-invoice product can only be sold alone: any other product in the sale is refused. */
    private static boolean requireNoExclusiveMix(List<PricedLine> lines) {
        boolean exclusive = lines.stream().anyMatch(PricedLine::requiresExclusiveInvoice);
        if (exclusive && lines.stream().map(PricedLine::productId).distinct().count() > 1) {
            throw new BusinessRuleException("EXCLUSIVE_PRODUCT_MIXED",
                    "A product with an exclusive invoice cannot be sold with other products");
        }
        return exclusive;
    }

    private static BigDecimal lineDiscount(Discount discount, BigDecimal gross) {
        if (discount == null) {
            return Money.ZERO;
        }
        BigDecimal amount = discount.type() == DiscountType.AMOUNT
                ? discount.value()
                : Money.percentOf(gross, discount.value());
        if (amount.compareTo(gross) > 0) {
            throw new BusinessRuleException("DISCOUNT_EXCEEDS_LINE", "The line discount is greater than the line");
        }
        return amount;
    }

    private static BigDecimal saleDiscountTotal(Discount discount, BigDecimal sharedBase) {
        if (discount == null) {
            return Money.ZERO;
        }
        BigDecimal amount = discount.type() == DiscountType.AMOUNT
                ? discount.value()
                : Money.percentOf(sharedBase, discount.value());
        if (amount.compareTo(sharedBase) > 0) {
            throw new BusinessRuleException("DISCOUNT_EXCEEDS_TOTAL", "The sale discount is greater than the sale");
        }
        return amount;
    }

    /** A line with its gross amount and its discount, before the sale discount is shared out. */
    private record Partial(PricedLine line, BigDecimal gross, BigDecimal lineDiscount, BigDecimal afterLine) {
    }
}
