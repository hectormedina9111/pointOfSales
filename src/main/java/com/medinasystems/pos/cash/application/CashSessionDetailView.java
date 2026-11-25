package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.cash.domain.CashMovement;
import com.medinasystems.pos.cash.domain.CashSession;
import com.medinasystems.pos.cash.domain.CashSessionStatus;
import com.medinasystems.pos.cash.domain.CountLine;

/** A cash session with its movements, as shown by the detail and returned by the closing. */
public record CashSessionDetailView(Long id, Long cashRegisterId, Long userId, CashSessionStatus status,
                                    Instant openedAt, BigDecimal openingAmount, Instant closedAt,
                                    BigDecimal expectedTotal, BigDecimal countedTotal, BigDecimal difference,
                                    List<CountLine> countDetail, String notes, List<CashMovementView> movements) {

    public static CashSessionDetailView from(CashSession session, List<CashMovement> movements) {
        return new CashSessionDetailView(session.getId(), session.getCashRegisterId(), session.getUserId(),
                session.getStatus(), session.getOpenedAt(), session.getOpeningAmount(), session.getClosedAt(),
                session.getExpectedTotal(), session.getCountedTotal(), session.getDifference(),
                session.getCountDetail(), session.getNotes(),
                movements.stream().map(CashMovementView::from).toList());
    }
}
