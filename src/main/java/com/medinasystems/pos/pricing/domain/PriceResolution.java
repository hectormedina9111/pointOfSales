package com.medinasystems.pos.pricing.domain;

import java.math.BigDecimal;

/**
 * A resolved unit price with tax included. {@code priceListId} is null when the base price was used.
 */
public record PriceResolution(BigDecimal unitPrice, Long priceListId, PriceSource source) {
}
