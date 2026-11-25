package com.medinasystems.pos.sync.infrastructure;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.application.SyncProperties;

/**
 * Moves the id sequence of every identity table to the first free id of the store's range, and every extra named
 * sequence listed in {@code pos.sync.extra-sequences} to the first free value of the range. Afterwards, every id the
 * store creates falls inside its range. Only tables in the current schema are touched.
 */
@Component
public class IdRangeSequenceAdjuster {

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z_][a-z0-9_]*$");
    private static final String IDENTITY_TABLES = """
            SELECT table_name FROM information_schema.columns
            WHERE table_schema = current_schema() AND column_name = 'id' AND is_identity = 'YES'
            """;
    private static final String FLYWAY_TABLE = "flyway_schema_history";

    private final JdbcTemplate jdbc;
    private final List<String> extraSequences;

    public IdRangeSequenceAdjuster(JdbcTemplate jdbc, SyncProperties properties) {
        this.jdbc = jdbc;
        this.extraSequences = properties.extraSequences() == null ? List.of() : List.copyOf(properties.extraSequences());
    }

    /**
     * Sets each sequence to the larger of the range start and the first id after the highest id already used inside
     * the range. A named sequence has no table to read the highest id from, so it is moved from its own state: its next
     * value, but never below the range start.
     *
     * @throws IllegalStateException when a name is not safe to put in SQL, or the range is exhausted
     */
    public void adjust(long rangeStart, long rangeEnd) {
        List<String> tables = jdbc.queryForList(IDENTITY_TABLES, String.class);
        for (String table : tables) {
            if (FLYWAY_TABLE.equals(table)) {
                continue;
            }
            if (!SAFE_NAME.matcher(table).matches()) {
                throw new IllegalStateException("Unexpected table name, the id ranges were not adjusted: " + table);
            }
            String sequence = jdbc.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String.class, table);
            if (sequence == null) {
                continue;
            }
            long next = jdbc.queryForObject("SELECT COALESCE(MAX(id) + 1, ?) FROM \"" + table
                    + "\" WHERE id BETWEEN ? AND ?", Long.class, rangeStart, rangeStart, rangeEnd);
            if (next > rangeEnd) {
                throw new IllegalStateException("The id range is exhausted for table " + table);
            }
            jdbc.queryForObject("SELECT setval(pg_get_serial_sequence(?, 'id'), ?, false)", Long.class, table, next);
        }
        for (String sequence : extraSequences) {
            adjustNamed(sequence, rangeStart, rangeEnd);
        }
    }

    private void adjustNamed(String sequence, long rangeStart, long rangeEnd) {
        if (!SAFE_NAME.matcher(sequence).matches()) {
            throw new IllegalStateException("Unexpected sequence name, the id ranges were not adjusted: " + sequence);
        }
        // The value the sequence would hand out next: last_value, or last_value + 1 once it has been used.
        long following = jdbc.queryForObject("SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END"
                + " FROM \"" + sequence + "\"", Long.class);
        long next = Math.max(rangeStart, following);
        if (next > rangeEnd) {
            throw new IllegalStateException("The id range is exhausted for sequence " + sequence);
        }
        jdbc.queryForObject("SELECT setval(CAST(? AS regclass), ?, false)", Long.class, sequence, next);
    }
}
