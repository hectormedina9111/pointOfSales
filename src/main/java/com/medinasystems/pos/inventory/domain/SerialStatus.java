package com.medinasystems.pos.inventory.domain;

/**
 * State of one physical unit of a product that tracks serial numbers. AVAILABLE units are in the branch where they are;
 * SOLD units left with a sale; IN_TRANSIT units were sent to another branch by a transfer and keep the origin branch until
 * the destination receives them; REMOVED units left the stock with an exit or a negative count.
 */
public enum SerialStatus {
    AVAILABLE,
    SOLD,
    IN_TRANSIT,
    REMOVED
}
