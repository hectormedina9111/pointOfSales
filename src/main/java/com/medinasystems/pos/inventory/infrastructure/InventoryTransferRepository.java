package com.medinasystems.pos.inventory.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.inventory.domain.InventoryTransfer;

public interface InventoryTransferRepository extends JpaRepository<InventoryTransfer, Long>,
        JpaSpecificationExecutor<InventoryTransfer> {

    Optional<InventoryTransfer> findBySendIdempotencyKey(String sendIdempotencyKey);

    Optional<InventoryTransfer> findByReceiveIdempotencyKey(String receiveIdempotencyKey);

    /** The transfer, locked until the transaction ends, so two receipts of one transfer run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from InventoryTransfer t where t.id = :id")
    Optional<InventoryTransfer> findForUpdate(@Param("id") long id);
}
