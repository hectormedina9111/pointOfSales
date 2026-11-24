package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;

/** Input for a price of one product in a list, from a minimum quantity. */
public record PriceListItemData(Long productId, BigDecimal minQuantity, BigDecimal price) {
}
