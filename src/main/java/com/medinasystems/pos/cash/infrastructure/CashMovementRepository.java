package com.medinasystems.pos.cash.infrastructure;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.cash.domain.CashMovement;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    /** Movements of a session in the order they were recorded. */
    List<CashMovement> findByCashSessionIdOrderByIdAsc(Long cashSessionId);
}
