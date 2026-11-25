package com.medinasystems.pos.sales.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    /** The holds of one register, newest first. */
    Page<Sale> findByStatusAndCashRegisterId(SaleStatus status, Long cashRegisterId, Pageable pageable);

    /** One hold of one register. Empty when the sale is not a hold of that register. */
    Optional<Sale> findByIdAndStatusAndCashRegisterId(Long id, SaleStatus status, Long cashRegisterId);

    /** The next internal number of this node. */
    @Query(value = "SELECT nextval('sale_internal_number_seq')", nativeQuery = true)
    Long nextInternalNumber();
}
