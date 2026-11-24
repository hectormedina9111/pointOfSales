package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.medinasystems.pos.pricing.domain.PriceSource;

/**
 * The resolved price of a product with its taxable base and tax. {@code priceListId} is null when
 * the base price was used.
 */
public record ProductPriceView(Long productId, Long priceListId, PriceSource source, BigDecimal quantity,
                               BigDecimal unitPrice, String taxCode, BigDecimal taxRate, BigDecimal taxableBase,
                               BigDecimal taxAmount, LocalDate date) {
}
