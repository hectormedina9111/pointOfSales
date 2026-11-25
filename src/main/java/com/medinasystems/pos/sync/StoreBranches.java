package com.medinasystems.pos.sync;

/**
 * Public API of the sync module for the modules that operate branches on the central node. A branch served by a store is
 * operated by that store, so the central node does not move its stock.
 */
public interface StoreBranches {

    /** True when a store node is assigned to the branch. False when the branch has no store or does not exist. */
    boolean isServedByStore(long branchId);
}
