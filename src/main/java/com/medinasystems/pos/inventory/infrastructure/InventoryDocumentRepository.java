package com.medinasystems.pos.inventory.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.inventory.domain.InventoryDocument;

public interface InventoryDocumentRepository extends JpaRepository<InventoryDocument, Long>,
        JpaSpecificationExecutor<InventoryDocument> {

    Optional<InventoryDocument> findByIdempotencyKey(String idempotencyKey);
}
