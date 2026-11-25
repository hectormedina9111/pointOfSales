package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * One line of a sale with its price already resolved, as the calculator needs it. The price includes tax. The
 * quantity is kept with three decimals; {@code discount} is null when the line has none. {@code unitPrice} is the price
 * the line is sold at; {@code listPrice} is the price of the list, and {@code priceOverridden} tells whether they differ
 * because a manager or the cashier changed the price.
 */
public record PricedLine(long productId, String sku, String description, BigDecimal quantity, BigDecimal unitPrice,
                         BigDecimal unitCost, String taxCode, BigDecimal taxRate, Long priceListId,
                         boolean requiresExclusiveInvoice, Discount discount, BigDecimal listPrice,
                         boolean priceOverridden, List<String> serialNumbers) {

    public PricedLine {
        serialNumbers = serialNumbers == null ? List.of() : List.copyOf(serialNumbers);
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
        }
        quantity = quantity.setScale(3, RoundingMode.HALF_UP);
        Objects.requireNonNull(unitPrice, "unit price is required");
        Objects.requireNonNull(listPrice, "list price is required");
        Objects.requireNonNull(taxRate, "tax rate is required");
        Objects.requireNonNull(taxCode, "tax code is required");
        unitPrice = Money.scale2(unitPrice);
        listPrice = Money.scale2(listPrice);
        taxRate = Money.scale2(taxRate);
        unitCost = unitCost == null ? null : Money.scale2(unitCost);
    }
}
