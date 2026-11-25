package com.medinasystems.pos.cash.domain;

import java.math.BigDecimal;

/**
 * One row of the count at closing: what the system expected for a payment method, what the cashier counted, and the
 * difference (counted minus expected). Stored as JSON in {@code cash_session.count_detail}.
 */
public record CountLine(String method, BigDecimal expected, BigDecimal counted, BigDecimal difference) {
}
