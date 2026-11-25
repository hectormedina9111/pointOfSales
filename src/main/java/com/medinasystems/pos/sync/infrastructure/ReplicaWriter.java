package com.medinasystems.pos.sync.infrastructure;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sync.application.SyncProtocol.Change;
import com.medinasystems.pos.sync.domain.SyncTable;
import com.medinasystems.pos.sync.domain.SyncTables;

/**
 * Store side only: writes the replicated rows into the local database.
 *
 * <p>Every method runs in one transaction and starts with {@code SET LOCAL session_replication_role = replica},
 * which turns off foreign key checks and triggers for that transaction only. That is why the order of rows does
 * not matter inside a batch. The database user must be allowed to set this (in Docker it is the owner, which is
 * a superuser). {@code SET LOCAL pos.sync_apply = 'on'} is a second guard that the change-log triggers check.
 *
 * <p>Rows arrive as JSON text. PostgreSQL does the mapping: {@code json_populate_record} and
 * {@code json_populate_recordset} match the JSON keys to the columns of the target table. Table names come only
 * from {@link SyncTables}, and column names come from information_schema.
 */
@Component
@Profile("store")
public class ReplicaWriter {

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z_][a-z0-9_]*$");
    private static final String APP_USER = "app_user";
    /** Password columns of app_user: a replicated row never overwrites a newer local password. */
    private static final Set<String> PASSWORD_COLUMNS = Set.of("password_hash", "must_change_password",
            "password_changed_at");
    private static final String NOT_OLDER_PASSWORD = "EXCLUDED.password_changed_at IS NULL"
            + " OR app_user.password_changed_at IS NULL"
            + " OR EXCLUDED.password_changed_at >= app_user.password_changed_at";
    private static final String COLUMNS_OF_TABLE = """
            SELECT column_name FROM information_schema.columns
            WHERE table_schema = current_schema() AND table_name = ?
            ORDER BY ordinal_position
            """;

    private final JdbcTemplate jdbc;
    private final StoreIdentityRepository identities;
    private final Map<String, List<String>> columns = new ConcurrentHashMap<>();

    public ReplicaWriter(JdbcTemplate jdbc, StoreIdentityRepository identities) {
        this.jdbc = jdbc;
        this.identities = identities;
    }

    /** Empties every replicated table, children first. Used before an initial snapshot. */
    @Transactional
    public void clearReplicatedTables() {
        enterApplyMode();
        List<SyncTable> tables = SyncTables.all();
        for (int i = tables.size() - 1; i >= 0; i--) {
            jdbc.update("DELETE FROM " + safe(tables.get(i).name()));
        }
    }

    /**
     * Writes one page of the initial snapshot. For a child table, the rows replace the children that the page
     * brings for its parents.
     */
    @Transactional
    public void applySnapshotPage(SyncTable table, List<String> rowsJson) {
        if (rowsJson.isEmpty()) {
            return;
        }
        enterApplyMode();
        String json = toJsonArray(rowsJson);
        if (table.kind() == SyncTable.Kind.MAIN) {
            upsert(table.name(), json);
            return;
        }
        String name = safe(table.name());
        String parent = safe(table.parentColumn());
        jdbc.update("DELETE FROM " + name + " WHERE " + parent + " IN (SELECT r." + parent
                + " FROM json_populate_recordset(NULL::" + name + ", ?::json) AS r)", json);
        insert(name, json);
    }

    /**
     * Applies one page of changes and moves the store's applied version to {@code lastVersion}, in the same
     * transaction. If anything fails, neither the rows nor the version are saved, and the page comes again.
     */
    @Transactional
    public void applyChanges(List<Change> changes, long lastVersion) {
        enterApplyMode();
        for (Change change : changes) {
            applyChange(change);
        }
        identities.findFirstByOrderByIdAsc().ifPresent(identity -> identity.advanceAppliedVersion(lastVersion));
    }

    private void applyChange(Change change) {
        SyncTable table = SyncTables.mainOf(change.entity())
                .orElseThrow(() -> new IllegalStateException("Change for a table that is not replicated: " + change.entity()));
        List<SyncTable> children = SyncTables.childrenOf(change.entity());
        if ("UPSERT".equals(change.operation()) && change.rowJson() != null) {
            upsert(table.name(), "[" + change.rowJson() + "]");
            for (SyncTable child : children) {
                // The entity's children arrive whole, so the local ones are replaced, even when none remain.
                jdbc.update("DELETE FROM " + safe(child.name()) + " WHERE " + safe(child.parentColumn()) + " = ?",
                        change.entityId());
                List<String> rows = change.children() == null
                        ? List.of() : change.children().getOrDefault(child.name(), List.of());
                if (!rows.isEmpty()) {
                    insert(child.name(), toJsonArray(rows));
                }
            }
            return;
        }
        for (SyncTable child : children) {
            jdbc.update("DELETE FROM " + safe(child.name()) + " WHERE " + safe(child.parentColumn()) + " = ?",
                    change.entityId());
        }
        jdbc.update("DELETE FROM " + safe(table.name()) + " WHERE id = ?", change.entityId());
    }

    /**
     * Insert, or update every column but id when the id already exists. In app_user the password columns keep the
     * local value when the incoming password is older (see NOT_OLDER_PASSWORD); the rest of the row always updates.
     */
    private void upsert(String table, String json) {
        String name = safe(table);
        String set = columnsOf(name).stream()
                .filter(column -> !"id".equals(column))
                .map(column -> column + " = " + assignment(name, column))
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow(() -> new IllegalStateException("Table has no columns to update: " + name));
        jdbc.update("INSERT INTO " + name + " SELECT r.* FROM json_populate_recordset(NULL::" + name
                + ", ?::json) AS r ON CONFLICT (id) DO UPDATE SET " + set, json);
    }

    private static String assignment(String table, String column) {
        if (!APP_USER.equals(table) || !PASSWORD_COLUMNS.contains(column)) {
            return "EXCLUDED." + column;
        }
        return "CASE WHEN " + NOT_OLDER_PASSWORD + " THEN EXCLUDED." + column + " ELSE app_user." + column + " END";
    }

    private void insert(String table, String json) {
        String name = safe(table);
        jdbc.update("INSERT INTO " + name + " SELECT r.* FROM json_populate_recordset(NULL::" + name
                + ", ?::json) AS r", json);
    }

    private List<String> columnsOf(String table) {
        return columns.computeIfAbsent(table, name -> jdbc.queryForList(COLUMNS_OF_TABLE, String.class, name));
    }

    private void enterApplyMode() {
        jdbc.execute("SET LOCAL session_replication_role = replica");
        jdbc.execute("SET LOCAL pos.sync_apply = 'on'");
    }

    private static String toJsonArray(List<String> rowsJson) {
        return "[" + String.join(",", rowsJson) + "]";
    }

    private static String safe(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Unexpected name, the replica was not written: " + name);
        }
        return name;
    }
}
