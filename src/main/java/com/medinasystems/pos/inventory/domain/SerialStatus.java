package com.medinasystems.pos.inventory.domain;

/**
 * State of one physical unit of a product that tracks serial numbers. AVAILABLE units are in the branch where they are;
 * SOLD units left with a sale; IN_TRANSIT units are on the way between branches (transfers, not implemented yet); REMOVED
 * units left the stock with an exit or a negative count.
 */
public enum SerialStatus {
    AVAILABLE,
    SOLD,
    IN_TRANSIT,
    REMOVED
}
