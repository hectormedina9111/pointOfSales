package com.medinasystems.pos.sales.infrastructure;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    /** The holds of one register, newest first. */
    Page<Sale> findByStatusAndCashRegisterId(SaleStatus status, Long cashRegisterId, Pageable pageable);

    /** One hold of one register. Empty when the sale is not a hold of that register. */
    Optional<Sale> findByIdAndStatusAndCashRegisterId(Long id, SaleStatus status, Long cashRegisterId);

    /** The sale with this receipt number and type. Receipts of returns carry their own suffix, so they never match a sale. */
    Optional<Sale> findByReceiptNumberAndType(String receiptNumber, SaleType type);

    /** The documents of one type that point to a sale, such as its returns. */
    List<Sale> findByOriginalSaleIdAndType(Long originalSaleId, SaleType type);

    /** A sale locked until the end of the transaction, so two returns of the same sale cannot run together. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    /** The next internal number of this node. */
    @Query(value = "SELECT nextval('sale_internal_number_seq')", nativeQuery = true)
    Long nextInternalNumber();
}
