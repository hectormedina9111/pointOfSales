package com.medinasystems.pos.inventory.infrastructure;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.inventory.domain.InventoryDocumentCounter;
import com.medinasystems.pos.inventory.domain.InventoryDocumentCounterKey;

public interface InventoryDocumentCounterRepository
        extends JpaRepository<InventoryDocumentCounter, InventoryDocumentCounterKey> {

    /**
     * Creates the counter of a branch and document type when it does not exist yet. ON CONFLICT DO NOTHING makes it safe
     * when two documents race. Returns the rows inserted (0 or 1).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO inventory_document_counter (branch_id, type, last_number, created_at, created_by,"
            + " updated_at, updated_by, version)"
            + " VALUES (:branchId, :documentType, 0, :now, :by, :now, :by, 0)"
            + " ON CONFLICT (branch_id, type) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("branchId") long branchId, @Param("documentType") String documentType,
                       @Param("now") Instant now, @Param("by") String by);

    /** The counter, locked until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from InventoryDocumentCounter c where c.branchId = :branchId and c.documentType = :documentType")
    Optional<InventoryDocumentCounter> findForUpdate(@Param("branchId") Long branchId,
                                                     @Param("documentType") String documentType);
}
