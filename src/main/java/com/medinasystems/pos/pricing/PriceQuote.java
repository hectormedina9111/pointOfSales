package com.medinasystems.pos.pricing;

import java.math.BigDecimal;

/**
 * The price of one unit with tax included, and the tax that applies to the product in the country.
 * {@code priceListId} is null when the base price was used; {@code taxRate} is a percentage such as 15.00.
 */
public record PriceQuote(BigDecimal unitPrice, String taxCode, BigDecimal taxRate, Long priceListId) {
}
