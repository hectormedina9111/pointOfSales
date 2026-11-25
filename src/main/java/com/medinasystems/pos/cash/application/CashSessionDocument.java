package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.cash.domain.CashMovement;
import com.medinasystems.pos.cash.domain.CashSession;
import com.medinasystems.pos.cash.domain.CountLine;

/**
 * The full snapshot of a cash session as the store uploads it (event CASH_SESSION): the session and all its movements.
 * The central node upserts it, so the latest snapshot wins. Ids are the store's own ids. Field names are part of the
 * protocol; the central node reads them in {@link CashSessionUploadHandler}.
 */
public record CashSessionDocument(long id, long branchId, long cashRegisterId, long userId, String status,
                                  String openedAt, BigDecimal openingAmount, String closedAt,
                                  BigDecimal expectedTotal, BigDecimal countedTotal, BigDecimal difference,
                                  List<CountLine> countDetail, String notes, String createdAt, String createdBy,
                                  String updatedAt, String updatedBy, Integer version,
                                  List<MovementDocument> movements) {

    public static final String EVENT_TYPE = "CASH_SESSION";

    public static CashSessionDocument of(CashSession session, long branchId, List<CashMovement> movements) {
        return new CashSessionDocument(session.getId(), branchId, session.getCashRegisterId(), session.getUserId(),
                session.getStatus().name(), text(session.getOpenedAt()), session.getOpeningAmount(),
                text(session.getClosedAt()), session.getExpectedTotal(), session.getCountedTotal(),
                session.getDifference(), session.getCountDetail(), session.getNotes(),
                text(session.getCreatedAt()), session.getCreatedBy(), text(session.getUpdatedAt()),
                session.getUpdatedBy(), session.getVersion(),
                movements.stream().map(MovementDocument::of).toList());
    }

    /** One movement of the session, as uploaded inside the snapshot. */
    public record MovementDocument(long id, String type, BigDecimal amount, String reason, String movedAt,
                                   long userId, Long authorizedBy, String createdAt, String createdBy,
                                   String updatedAt, String updatedBy, Integer version) {

        static MovementDocument of(CashMovement movement) {
            return new MovementDocument(movement.getId(), movement.getType().name(), movement.getAmount(),
                    movement.getReason(), text(movement.getMovedAt()), movement.getUserId(),
                    movement.getAuthorizedBy(), text(movement.getCreatedAt()), movement.getCreatedBy(),
                    text(movement.getUpdatedAt()), movement.getUpdatedBy(), movement.getVersion());
        }
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
