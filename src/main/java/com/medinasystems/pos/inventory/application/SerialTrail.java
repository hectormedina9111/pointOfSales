package com.medinasystems.pos.inventory.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.domain.InventoryMovementSerial;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.infrastructure.InventoryMovementSerialRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;

/**
 * The serial units that some movements moved, with the links between them: what an upload carries besides the movements
 * themselves. Read in the transaction that wrote the movements, so the units show their state after them.
 */
@Component
public class SerialTrail {

    private final ProductSerialRepository units;
    private final InventoryMovementSerialRepository links;

    public SerialTrail(ProductSerialRepository units, InventoryMovementSerialRepository links) {
        this.units = units;
        this.links = links;
    }

    /** The units and links of the given movements. Empty when none of them moved a serial unit. */
    public Trail of(List<StockMovement> moved) {
        if (moved.isEmpty()) {
            return Trail.EMPTY;
        }
        List<Long> movementIds = moved.stream().map(StockMovement::id).toList();
        List<InventoryMovementSerial> found = links.findByMovementIdIn(movementIds);
        if (found.isEmpty()) {
            return Trail.EMPTY;
        }
        List<Long> serialIds = found.stream().map(InventoryMovementSerial::getSerialId).distinct().toList();
        return new Trail(units.findByIdIn(serialIds), found);
    }

    /** The serial units and the movement links of a set of movements. */
    public record Trail(List<ProductSerial> serials, List<InventoryMovementSerial> links) {

        public static final Trail EMPTY = new Trail(List.of(), List.of());
    }
}
