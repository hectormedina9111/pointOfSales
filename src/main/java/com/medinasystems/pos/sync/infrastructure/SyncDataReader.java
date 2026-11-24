package com.medinasystems.pos.sync.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.domain.SyncTable;
import com.medinasystems.pos.sync.domain.SyncTables;

/**
 * Reads the replicated tables and the change log. It runs on both profiles (the store-node service of the central
 * node also needs the latest version, and the table exists everywhere), but only the central node calls the
 * methods that serve stores. Table names are interpolated into SQL, so every method takes a {@link SyncTable}
 * from the registry, never a raw name from a request.
 */
@Component
public class SyncDataReader {

    /** A changed entity, before its current row is read. */
    public record ChangedEntity(long version, String entity, long entityId, String operation) {
    }

    /** A main-table row with its id, used to page snapshots. */
    public record IdRow(long id, String rowJson) {
    }

    private final JdbcTemplate jdbc;

    public SyncDataReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Highest version in the change log, or 0 when it is empty. */
    public long latestVersion() {
        Long version = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM sync_change_log", Long.class);
        return version == null ? 0L : version;
    }

    /** Main table page ordered by id. */
    public List<IdRow> mainPage(SyncTable table, long afterId, int limit) {
        return jdbc.query("SELECT t.id, row_to_json(t)::text AS row_json FROM " + table.name()
                        + " t WHERE t.id > ? ORDER BY t.id LIMIT ?",
                (rs, rowNum) -> new IdRow(rs.getLong("id"), rs.getString("row_json")), afterId, limit);
    }

    /** Parent ids of a child table, one page, ordered. */
    public List<Long> parentIdPage(SyncTable table, long afterId, int limit) {
        return jdbc.queryForList("SELECT DISTINCT " + table.parentColumn() + " FROM " + table.name()
                + " WHERE " + table.parentColumn() + " > ? ORDER BY " + table.parentColumn() + " LIMIT ?",
                Long.class, afterId, limit);
    }

    /** All rows of a child table whose parent id is in {@code parentIds}. */
    public List<String> childRowsForParents(SyncTable table, List<Long> parentIds) {
        String placeholders = parentIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.queryForList("SELECT row_to_json(c)::text FROM " + table.name() + " c WHERE c."
                + table.parentColumn() + " IN (" + placeholders + ") ORDER BY c." + table.parentColumn(),
                String.class, parentIds.toArray());
    }

    /**
     * Entities that changed after {@code sinceVersion}, one per entity, with the latest operation of each. The page
     * is ordered by the highest version of each entity, so {@code lastVersion} is safe to ask from next time: an
     * entity that changed again later is sent again with its current state.
     */
    public List<ChangedEntity> changedEntities(long sinceVersion, int limit) {
        return jdbc.query("""
                SELECT entity, entity_id, MAX(version) AS v, (array_agg(operation ORDER BY version DESC))[1] AS op
                FROM sync_change_log
                WHERE version > ?
                GROUP BY entity, entity_id
                ORDER BY v
                LIMIT ?
                """, (rs, rowNum) -> new ChangedEntity(rs.getLong("v"), rs.getString("entity"),
                rs.getLong("entity_id"), rs.getString("op")), sinceVersion, limit);
    }

    /** Current row of a main table, or empty when it no longer exists. */
    public Optional<String> rowJson(SyncTable table, long id) {
        List<String> rows = jdbc.queryForList("SELECT row_to_json(t)::text FROM " + table.name()
                + " t WHERE t.id = ?", String.class, id);
        return rows.stream().findFirst();
    }

    /** Current rows of every child table of an entity, keyed by child table name. */
    public Map<String, List<String>> childRows(String entity, long entityId) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (SyncTable child : SyncTables.childrenOf(entity)) {
            List<String> rows = new ArrayList<>(jdbc.queryForList("SELECT row_to_json(c)::text FROM "
                    + child.name() + " c WHERE c." + child.parentColumn() + " = ?", String.class, entityId));
            result.put(child.name(), rows);
        }
        return result;
    }
}
