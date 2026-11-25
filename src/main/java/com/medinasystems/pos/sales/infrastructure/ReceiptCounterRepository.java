package com.medinasystems.pos.sales.infrastructure;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.ReceiptCounter;

public interface ReceiptCounterRepository extends JpaRepository<ReceiptCounter, Long> {

    /**
     * Creates the counter of a register when it does not exist yet. ON CONFLICT DO NOTHING makes it safe when two charges
     * race: the second insert waits for the first transaction and then does nothing. Returns the rows inserted (0 or 1).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO cash_register_counter (cash_register_id, last_number, created_at, created_by,"
            + " updated_at, updated_by, version)"
            + " VALUES (:cashRegisterId, 0, :now, :by, :now, :by, 0)"
            + " ON CONFLICT (cash_register_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("cashRegisterId") long cashRegisterId, @Param("now") Instant now,
                       @Param("by") String by);

    /** The counter of a register, locked until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReceiptCounter c where c.cashRegisterId = :cashRegisterId")
    Optional<ReceiptCounter> findForUpdate(@Param("cashRegisterId") Long cashRegisterId);
}
