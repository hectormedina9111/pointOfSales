package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.cash.domain.CashSession;
import com.medinasystems.pos.cash.domain.CashSessionStatus;
import com.medinasystems.pos.cash.domain.CountLine;

/**
 * A cash session without its movements, as listed. {@code branchId} is the branch of its register, so the central node
 * can show which store each session came from.
 */
public record CashSessionView(Long id, Long cashRegisterId, Long branchId, Long userId, CashSessionStatus status,
                              Instant openedAt, BigDecimal openingAmount, Instant closedAt, BigDecimal expectedTotal,
                              BigDecimal countedTotal, BigDecimal difference, List<CountLine> countDetail,
                              String notes) {

    public static CashSessionView from(CashSession session, Long branchId) {
        return new CashSessionView(session.getId(), session.getCashRegisterId(), branchId, session.getUserId(),
                session.getStatus(), session.getOpenedAt(), session.getOpeningAmount(), session.getClosedAt(),
                session.getExpectedTotal(), session.getCountedTotal(), session.getDifference(),
                session.getCountDetail(), session.getNotes());
    }
}
