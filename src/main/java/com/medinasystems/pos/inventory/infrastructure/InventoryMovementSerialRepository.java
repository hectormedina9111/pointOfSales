package com.medinasystems.pos.inventory.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.inventory.domain.InventoryMovementSerial;

public interface InventoryMovementSerialRepository extends JpaRepository<InventoryMovementSerial, Long> {

    /** The serial links of the given movements. */
    List<InventoryMovementSerial> findByMovementIdIn(Collection<Long> movementIds);

    /** The serial links of one unit, oldest first, for its trace. */
    List<InventoryMovementSerial> findBySerialIdOrderByIdAsc(long serialId);
}
