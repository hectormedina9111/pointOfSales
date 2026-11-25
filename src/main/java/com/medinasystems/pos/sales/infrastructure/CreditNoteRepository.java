package com.medinasystems.pos.sales.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.CreditNote;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long>, JpaSpecificationExecutor<CreditNote> {

    /** The note issued by one return, if any. */
    Optional<CreditNote> findByReturnSaleId(Long returnSaleId);

    Optional<CreditNote> findByNumber(String number);

    /** The note with its row locked until the end of the transaction: its balance is changed under this lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from CreditNote n where n.id = :id")
    Optional<CreditNote> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from CreditNote n where n.number = :number")
    Optional<CreditNote> findByNumberForUpdate(@Param("number") String number);
}
