package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.medinasystems.pos.cash.domain.CashMovement;
import com.medinasystems.pos.cash.domain.CashMovementType;

public record CashMovementView(Long id, Long cashSessionId, CashMovementType type, BigDecimal amount, String reason,
                               Instant movedAt, Long userId, Long authorizedBy) {

    public static CashMovementView from(CashMovement movement) {
        return new CashMovementView(movement.getId(), movement.getCashSessionId(), movement.getType(),
                movement.getAmount(), movement.getReason(), movement.getMovedAt(), movement.getUserId(),
                movement.getAuthorizedBy());
    }
}
