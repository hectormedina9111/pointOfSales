package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.util.Map;

import com.medinasystems.pos.cash.domain.CashSessionStatus;

/** What the session should hold right now, per payment method, before the count. */
public record SessionSummaryView(Long sessionId, CashSessionStatus status, Map<String, BigDecimal> expectedByMethod,
                                 BigDecimal expectedTotal) {
}
