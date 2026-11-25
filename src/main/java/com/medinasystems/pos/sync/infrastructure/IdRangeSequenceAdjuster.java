package com.medinasystems.pos.sync.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.application.SyncProperties;

/**
 * Keeps the id sequences of a node inside the node's range. A store moves every identity sequence, and every extra named
 * sequence listed in {@code pos.sync.extra-sequences}, to the first free id of its range. Then it caps each sequence at
 * the last id of the range with MAXVALUE, so PostgreSQL refuses to hand out an id beyond the range instead of invading
 * another node's range. The central node caps its sequences at the central range without moving them. Only tables in the
 * current schema are touched.
 */
@Component
public class IdRangeSequenceAdjuster {

    /** Last id of the central node. The store ranges start above it. */
    public static final long CENTRAL_RANGE_END = 999_999_999L;

    private static final Pattern SAFE_NAME = Pattern.compile("^[a-z_][a-z0-9_]*$");
    /** A sequence name as pg_get_serial_sequence returns it: optionally schema-qualified, and never quoted here. */
    private static final Pattern SAFE_SEQUENCE = Pattern.compile("^([a-z_][a-z0-9_]*\\.)?[a-z_][a-z0-9_]*$");
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
     * Store side. Sets each sequence to the larger of the range start and the first id after the highest id already used
     * inside the range, then caps it at the range end. A named sequence has no table to read the highest id from, so it
     * is moved from its own state: its next value, but never below the range start.
     *
     * @throws IllegalStateException when a name is not safe to put in SQL, or the range is exhausted
     */
    public void adjust(long rangeStart, long rangeEnd) {
        for (Map.Entry<String, String> identity : identitySequences().entrySet()) {
            String table = identity.getKey();
            String sequence = identity.getValue();
            long next = jdbc.queryForObject("SELECT COALESCE(MAX(id) + 1, ?) FROM \"" + table
                    + "\" WHERE id BETWEEN ? AND ?", Long.class, rangeStart, rangeStart, rangeEnd);
            if (next > rangeEnd) {
                throw new IllegalStateException("The id range is exhausted for table " + table);
            }
            setNext(sequence, next);
            cap(sequence, rangeEnd);
        }
        for (String sequence : extraSequences) {
            adjustNamed(sequence, rangeStart, rangeEnd);
        }
    }

    /**
     * Central node, at every startup. Every identity sequence and every extra sequence is capped at the central range, so
     * PostgreSQL refuses an id beyond it. Nothing is moved: a sequence already past the central range fails the startup.
     *
     * @throws IllegalStateException when a sequence is already past the central range, or a name is not safe
     */
    public void capCentral() {
        for (String sequence : allSequences()) {
            if (nextValue(sequence) > CENTRAL_RANGE_END) {
                throw new IllegalStateException("The central sequence " + sequence
                        + " is already past the central range, which ends at " + CENTRAL_RANGE_END);
            }
            cap(sequence, CENTRAL_RANGE_END);
        }
    }

    private void adjustNamed(String sequence, long rangeStart, long rangeEnd) {
        long next = Math.max(rangeStart, nextValue(sequence));
        if (next > rangeEnd) {
            throw new IllegalStateException("The id range is exhausted for sequence " + sequence);
        }
        setNext(sequence, next);
        cap(sequence, rangeEnd);
    }

    /** Table name to its sequence, for every identity table of the schema that has one. */
    private Map<String, String> identitySequences() {
        Map<String, String> sequences = new LinkedHashMap<>();
        for (String table : jdbc.queryForList(IDENTITY_TABLES, String.class)) {
            if (FLYWAY_TABLE.equals(table)) {
                continue;
            }
            if (!SAFE_NAME.matcher(table).matches()) {
                throw new IllegalStateException("Unexpected table name, the id ranges were not adjusted: " + table);
            }
            String sequence = jdbc.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String.class, table);
            if (sequence != null) {
                sequences.put(table, sequence);
            }
        }
        return sequences;
    }

    private List<String> allSequences() {
        List<String> all = new ArrayList<>(identitySequences().values());
        all.addAll(extraSequences);
        return all;
    }

    /** The value the sequence would hand out next: last_value, or last_value + 1 once it has been used. */
    private long nextValue(String sequence) {
        return jdbc.queryForObject("SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM "
                + checked(sequence), Long.class);
    }

    private void setNext(String sequence, long next) {
        jdbc.queryForObject("SELECT setval(CAST(? AS regclass), ?, false)", Long.class, checked(sequence), next);
    }

    /**
     * Caps the sequence. It runs after {@link #setNext}, so the current value is always inside the new maximum: setting
     * the maximum first could fail on a sequence whose value is already past it.
     */
    private void cap(String sequence, long max) {
        jdbc.execute("ALTER SEQUENCE " + checked(sequence) + " MAXVALUE " + max);
    }

    /** The name goes into SQL without quotes, so only plain names are accepted. */
    private static String checked(String sequence) {
        if (!SAFE_SEQUENCE.matcher(sequence).matches()) {
            throw new IllegalStateException("Unexpected sequence name, the id ranges were not adjusted: " + sequence);
        }
        return sequence;
    }
}
