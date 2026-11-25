package com.medinasystems.pos.sync.domain;

import java.util.List;
import java.util.Optional;

/**
 * The replication registry: every table the stores receive, in the order they are loaded in the initial
 * snapshot. The names are constants in the code; request input is only ever compared against this list, never
 * put into SQL.
 *
 * <p>Main tables come first, in dependency order, and then the child tables. The store applies changes with
 * foreign key checks disabled, so the order matters only for the snapshot's readability.
 */
public final class SyncTables {

    private static final List<SyncTable> ALL = List.of(
            SyncTable.main("country"),
            SyncTable.main("company"),
            SyncTable.main("role"),
            SyncTable.main("privilege"),
            SyncTable.main("app_user"),
            SyncTable.main("branch"),
            SyncTable.main("category"),
            SyncTable.main("unit_of_measure"),
            SyncTable.main("tax"),
            SyncTable.main("product"),
            SyncTable.main("product_tax"),
            SyncTable.main("price_list"),
            SyncTable.main("customer"),
            SyncTable.main("credit_note"),
            SyncTable.main("sale_return_claim"),
            SyncTable.main("price_list_item"),
            SyncTable.main("cash_register"),
            SyncTable.main("payment_method"),
            SyncTable.child("user_role", "app_user", "user_id"),
            SyncTable.child("user_branch", "app_user", "user_id"),
            SyncTable.child("role_privilege", "role", "role_id"));

    private SyncTables() {
    }

    /** All replicated tables, in snapshot order. */
    public static List<SyncTable> all() {
        return ALL;
    }

    /** Looks a table up by name; empty when the name is not replicated. */
    public static Optional<SyncTable> find(String name) {
        return ALL.stream().filter(table -> table.name().equals(name)).findFirst();
    }

    /** The main table of an entity, or empty when the entity is not a main table. */
    public static Optional<SyncTable> mainOf(String entity) {
        return ALL.stream()
                .filter(table -> table.kind() == SyncTable.Kind.MAIN && table.name().equals(entity))
                .findFirst();
    }

    /** The child tables that belong to an entity, in snapshot order. */
    public static List<SyncTable> childrenOf(String entity) {
        return ALL.stream()
                .filter(table -> table.kind() == SyncTable.Kind.CHILD && table.parentEntity().equals(entity))
                .toList();
    }
}
