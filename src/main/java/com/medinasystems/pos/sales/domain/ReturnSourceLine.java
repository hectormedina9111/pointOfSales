package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * A line a return can take from: the original line with what a return line copies from it (product, sku, price, tax),
 * the quantities already returned, and the quantity still returnable once the claims of other returns are counted.
 * The original line may live in this node's database or come from the central node for a sale of another branch.
 *
 * <p>{@code total} is the net amount paid for the whole line. {@code returnedQuantity} is the ledger quantity: when a
 * return takes all of {@code quantity - returnedQuantity}, it refunds {@code total} minus what was refunded before.
 * {@code returnableQuantity} is the cap: it can be smaller, because other returns hold quantities.
 *
 * <p>{@code tracksSerials} is true when the line sold serial numbers. {@code serialNumbers} are all the numbers sold in the
 * line and {@code returnableSerialNumbers} those that did not come back yet. {@code serialMode} is the serial mode of the
 * product now (NONE, MANUAL or LIST).
 */
public record ReturnSourceLine(long id, long productId, String sku, String description, BigDecimal quantity,
                               BigDecimal returnedQuantity, BigDecimal returnableQuantity, BigDecimal total,
                               BigDecimal unitPrice, BigDecimal unitCost, String taxCode, BigDecimal taxRate,
                               BigDecimal listPrice, Long priceListId, boolean priceOverridden, boolean tracksSerials,
                               List<String> serialNumbers, List<String> returnableSerialNumbers, String serialMode) {

    /** The line of a sale of this node, with the quantity held by claims (see {@link SaleReturnClaim}). */
    public static ReturnSourceLine of(SaleLine line, BigDecimal held, String serialMode) {
        BigDecimal returnable = line.getQuantity().subtract(line.getReturnedQuantity()).subtract(held);
        List<String> serials = line.getSerialNumbers();
        return new ReturnSourceLine(line.getId(), line.getProductId(), line.getSku(), line.getDescription(),
                line.getQuantity(), line.getReturnedQuantity(), returnable.signum() < 0 ? Money.ZERO : returnable,
                line.getTotal(), line.getUnitPrice(), line.getUnitCost(), line.getTaxCode(), line.getTaxRate(),
                line.getListPrice(), line.getPriceListId(), line.isPriceOverridden(), !serials.isEmpty(), serials,
                line.getReturnableSerialNumbers(), serialMode);
    }

    /** The quantity that a return can still take from the ledger: what is left, without the claims. */
    public BigDecimal ledgerRemaining() {
        return quantity.subtract(returnedQuantity);
    }
}
