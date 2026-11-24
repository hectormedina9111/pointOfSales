package com.medinasystems.pos.sync.application;

/**
 * Store side: the central node answered SNAPSHOT_REQUIRED, so the changes this store needs were purged and it must
 * load a full snapshot again. Carries no request data, so it never leaks the token.
 */
public class SnapshotRequiredException extends RuntimeException {

    public SnapshotRequiredException() {
        super("The central node requires a new snapshot");
    }
}
