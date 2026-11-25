package com.medinasystems.pos.sales.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.CreditNoteRedemption;

public interface CreditNoteRedemptionRepository extends JpaRepository<CreditNoteRedemption, Long> {

    Optional<CreditNoteRedemption> findByRedemptionKey(String redemptionKey);

    /** The note of a redemption, read without loading the redemption: its state is read after the note is locked. */
    @Query("select r.creditNoteId from CreditNoteRedemption r where r.redemptionKey = :redemptionKey")
    Optional<Long> findCreditNoteIdByRedemptionKey(@Param("redemptionKey") String redemptionKey);

    /** The redemptions of one note, paged in the database. */
    Page<CreditNoteRedemption> findByCreditNoteId(Long creditNoteId, Pageable pageable);
}
