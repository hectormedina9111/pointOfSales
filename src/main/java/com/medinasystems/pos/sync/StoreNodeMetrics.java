package com.medinasystems.pos.sync;

/**
 * Figures about a store that the stores panel shows and that another module owns. The owning module implements this
 * interface, so the sync module never depends on the module that holds the data.
 */
public interface StoreNodeMetrics {

    /** Number of return conflicts recorded for the store node: returns that exceeded the quantity left on their line. */
    long returnConflicts(long storeNodeId);
}
