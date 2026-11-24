package com.medinasystems.pos.catalog;

import java.math.BigDecimal;

/**
 * What pricing needs about a product in one country. {@code salePrice} includes tax;
 * {@code taxRate} is a percentage such as 15.00.
 */
public record ProductPricingInfo(long productId, boolean active, BigDecimal salePrice,
                                 String taxCode, BigDecimal taxRate) {
}
