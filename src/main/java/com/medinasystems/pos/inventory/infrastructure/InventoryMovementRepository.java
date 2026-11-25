package com.medinasystems.pos.inventory.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.inventory.domain.InventoryMovement;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long>,
        JpaSpecificationExecutor<InventoryMovement> {
}
