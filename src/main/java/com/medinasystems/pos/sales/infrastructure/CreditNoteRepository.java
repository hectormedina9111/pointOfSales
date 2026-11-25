package com.medinasystems.pos.sales.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.sales.domain.CreditNote;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long>, JpaSpecificationExecutor<CreditNote> {

    /** The note issued by one return, if any. */
    Optional<CreditNote> findByReturnSaleId(Long returnSaleId);
}
