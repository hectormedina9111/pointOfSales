package com.medinasystems.pos.pricing.domain;

/** How a price list sets prices: a price per product and quantity, or a discount on the base price. */
public enum PricingMethod {
    FIXED_PRICE,
    PERCENT_OFF_BASE
}
