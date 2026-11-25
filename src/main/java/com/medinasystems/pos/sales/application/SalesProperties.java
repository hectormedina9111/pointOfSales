package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sales settings bound from {@code pos.sales.*}.
 *
 * @param maxDiscountPercent largest discount, in percent, that a cashier may give without a manager's authorization
 *                           (env POS_SALES_MAX_DISCOUNT_PERCENT, default 10)
 */
@ConfigurationProperties("pos.sales")
public record SalesProperties(@DefaultValue("10") BigDecimal maxDiscountPercent) {
}
