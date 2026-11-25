package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;

/**
 * A line a return can take from: the original line with what a return line copies from it (product, sku, price, tax),
 * the quantities already returned, and the quantity still returnable once the claims of other returns are counted.
 * The original line may live in this node's database or come from the central node for a sale of another branch.
 *
 * <p>{@code total} is the net amount paid for the whole line. {@code returnedQuantity} is the ledger quantity: when a
 * return takes all of {@code quantity - returnedQuantity}, it refunds {@code total} minus what was refunded before.
 * {@code returnableQuantity} is the cap: it can be smaller, because other returns hold quantities.
 */
public record ReturnSourceLine(long id, long productId, String sku, String description, BigDecimal quantity,
                               BigDecimal returnedQuantity, BigDecimal returnableQuantity, BigDecimal total,
                               BigDecimal unitPrice, BigDecimal unitCost, String taxCode, BigDecimal taxRate,
                               BigDecimal listPrice, Long priceListId, boolean priceOverridden) {

    /** The line of a sale of this node, with the quantity held by claims (see {@link SaleReturnClaim}). */
    public static ReturnSourceLine of(SaleLine line, BigDecimal held) {
        BigDecimal returnable = line.getQuantity().subtract(line.getReturnedQuantity()).subtract(held);
        return new ReturnSourceLine(line.getId(), line.getProductId(), line.getSku(), line.getDescription(),
                line.getQuantity(), line.getReturnedQuantity(), returnable.signum() < 0 ? Money.ZERO : returnable,
                line.getTotal(), line.getUnitPrice(), line.getUnitCost(), line.getTaxCode(), line.getTaxRate(),
                line.getListPrice(), line.getPriceListId(), line.isPriceOverridden());
    }

    /** The quantity that a return can still take from the ledger: what is left, without the claims. */
    public BigDecimal ledgerRemaining() {
        return quantity.subtract(returnedQuantity);
    }
}
