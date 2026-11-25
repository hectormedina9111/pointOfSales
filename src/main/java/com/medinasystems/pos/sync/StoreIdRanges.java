package com.medinasystems.pos.sync;

/**
 * Public API of the sync module for the upload handlers. A store creates its records with ids from its own range, so the
 * central node checks that every id of an upload lies in the range of the store that sent it.
 */
public interface StoreIdRanges {

    /** True when the id is inside the id range of the store node. False when the store node does not exist. */
    boolean contains(long storeNodeId, long id);
}
