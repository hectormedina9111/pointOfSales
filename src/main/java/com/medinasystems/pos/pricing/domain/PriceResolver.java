package com.medinasystems.pos.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Chooses the unit price of a product. Pure logic with no persistence: the caller loads the list,
 * the items of the product and the base price.
 *
 * <ol>
 *   <li>If the list is active and valid on the date, a FIXED_PRICE list uses the item with the
 *       highest min_quantity that does not exceed the quantity; a PERCENT_OFF_BASE list applies
 *       its percentage to the base price.</li>
 *   <li>Otherwise, including when no item matches the quantity, the base price is used.</li>
 * </ol>
 */
public final class PriceResolver {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PriceResolver() {
    }

    /**
     * @param list      the price list chosen for the sale; null when there is none
     * @param items     the items of the list for this product; ignored unless the list is FIXED_PRICE
     * @param basePrice the product's sale price, with tax included
     */
    public static PriceResolution resolve(PriceList list, List<PriceListItem> items, BigDecimal basePrice,
                                          BigDecimal quantity, LocalDate date) {
        if (list != null && list.isUsableOn(date)) {
            if (list.getPricingMethod() == PricingMethod.PERCENT_OFF_BASE) {
                return new PriceResolution(percentOff(basePrice, list.getPercent()), list.getId(),
                        PriceSource.PRICE_LIST);
            }
            return items.stream()
                    .filter(item -> item.getMinQuantity().compareTo(quantity) <= 0)
                    .max(Comparator.comparing(PriceListItem::getMinQuantity))
                    .map(item -> new PriceResolution(item.getPrice(), list.getId(), PriceSource.PRICE_LIST))
                    .orElseGet(() -> new PriceResolution(basePrice, null, PriceSource.BASE_PRICE));
        }
        return new PriceResolution(basePrice, null, PriceSource.BASE_PRICE);
    }

    /** Discounted price: base * (100 - percent) / 100, rounded HALF_UP to 2 decimals. */
    static BigDecimal percentOff(BigDecimal basePrice, BigDecimal percent) {
        return basePrice.multiply(HUNDRED.subtract(percent)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
