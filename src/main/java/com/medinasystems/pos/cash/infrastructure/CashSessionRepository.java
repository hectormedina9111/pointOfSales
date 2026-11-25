package com.medinasystems.pos.cash.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.cash.domain.CashSession;
import com.medinasystems.pos.cash.domain.CashSessionStatus;

public interface CashSessionRepository extends JpaRepository<CashSession, Long>, JpaSpecificationExecutor<CashSession> {

    boolean existsByCashRegisterIdAndStatus(Long cashRegisterId, CashSessionStatus status);

    boolean existsByUserIdAndStatus(Long userId, CashSessionStatus status);

    Optional<CashSession> findByUserIdAndStatus(Long userId, CashSessionStatus status);
}
