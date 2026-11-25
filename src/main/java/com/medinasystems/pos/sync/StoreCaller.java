package com.medinasystems.pos.sync;

/**
 * Public constants for the store-token authentication of {@code /api/v1/sync/**}. A controller of another module that
 * serves store calls reads the id of the calling store from the request attribute named here.
 */
public final class StoreCaller {

    /** Request attribute that holds the id of the store node whose token was accepted (a long). */
    public static final String NODE_ATTRIBUTE = "posStoreNodeId";

    private StoreCaller() {
    }
}
