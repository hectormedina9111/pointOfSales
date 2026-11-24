package com.medinasystems.pos.sync.domain;

/**
 * A table that is replicated from the central node to the stores.
 *
 * @param name         table name in the database; it is also the entity name in sync_change_log
 * @param kind         MAIN: each row is an entity with its own id; CHILD: rows belong to an entity through a column
 * @param parentEntity for CHILD tables, the entity they belong to; null for MAIN tables
 * @param parentColumn for CHILD tables, the column that holds the id of the parent entity; null for MAIN tables
 */
public record SyncTable(String name, Kind kind, String parentEntity, String parentColumn) {

    public enum Kind {
        MAIN, CHILD
    }

    public static SyncTable main(String name) {
        return new SyncTable(name, Kind.MAIN, null, null);
    }

    public static SyncTable child(String name, String parentEntity, String parentColumn) {
        return new SyncTable(name, Kind.CHILD, parentEntity, parentColumn);
    }
}
