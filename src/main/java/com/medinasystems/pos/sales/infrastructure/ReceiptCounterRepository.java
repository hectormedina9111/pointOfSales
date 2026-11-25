package com.medinasystems.pos.sales.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.ReceiptCounter;

public interface ReceiptCounterRepository extends JpaRepository<ReceiptCounter, Long> {

    /** The counter of a register, locked until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReceiptCounter c where c.cashRegisterId = :cashRegisterId")
    Optional<ReceiptCounter> findForUpdate(@Param("cashRegisterId") Long cashRegisterId);
}
