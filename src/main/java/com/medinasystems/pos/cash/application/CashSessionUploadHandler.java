package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Central side: stores the snapshot of a cash session uploaded by a store. The session is upserted with the store's id
 * (the latest snapshot wins) and its movements are inserted once. Everything is read and checked before the first write.
 * The central node checks only completeness and the branch: it does not revalidate the rules of the store, such as one
 * open session per cashier (that rule is checked by the store before it opens a session).
 */
@Component
@Profile("central")
public class CashSessionUploadHandler implements UploadHandler {

    private static final String OPEN = "OPEN";
    private static final String CLOSED = "CLOSED";
    private static final String DEPOSIT = "DEPOSIT";
    private static final String WITHDRAWAL = "WITHDRAWAL";

    private static final String SESSION_SQL = "INSERT INTO cash_session (id, cash_register_id, user_id, status,"
            + " opened_at, opening_amount, closed_at, expected_total, counted_total, difference, count_detail, notes,"
            + " created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT (id) DO UPDATE SET cash_register_id = EXCLUDED.cash_register_id,"
            + " user_id = EXCLUDED.user_id, status = EXCLUDED.status, opened_at = EXCLUDED.opened_at,"
            + " opening_amount = EXCLUDED.opening_amount, closed_at = EXCLUDED.closed_at,"
            + " expected_total = EXCLUDED.expected_total, counted_total = EXCLUDED.counted_total,"
            + " difference = EXCLUDED.difference, count_detail = EXCLUDED.count_detail, notes = EXCLUDED.notes,"
            + " updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by, version = EXCLUDED.version";

    private static final String MOVEMENT_SQL = "INSERT INTO cash_movement (id, cash_session_id, type, amount, reason,"
            + " moved_at, user_id, authorized_by, created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    public CashSessionUploadHandler(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String eventType() {
        return CashSessionDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        try {
            return store(payload, storeBranchId);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
    }

    private UploadOutcome store(JsonNode payload, Long storeBranchId) {
        long id = UploadPayload.longField(payload, "id");
        long branchId = UploadPayload.longField(payload, "branchId");
        if (storeBranchId == null || !storeBranchId.equals(branchId)) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        long registerId = UploadPayload.longField(payload, "cashRegisterId");
        long userId = UploadPayload.longField(payload, "userId");
        String status = UploadPayload.text(payload, "status");
        if (!OPEN.equals(status) && !CLOSED.equals(status)) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        Instant openedAt = UploadPayload.instant(payload, "openedAt");
        BigDecimal openingAmount = UploadPayload.decimal(payload, "openingAmount");
        Instant closedAt = UploadPayload.optionalInstant(payload, "closedAt");
        BigDecimal expectedTotal = UploadPayload.optionalDecimal(payload, "expectedTotal");
        BigDecimal countedTotal = UploadPayload.optionalDecimal(payload, "countedTotal");
        BigDecimal difference = UploadPayload.optionalDecimal(payload, "difference");
        String countDetail = jsonMapper.writeValueAsString(UploadPayload.array(payload, "countDetail"));
        String notes = UploadPayload.optionalText(payload, "notes");
        Instant createdAt = UploadPayload.instant(payload, "createdAt");
        String createdBy = UploadPayload.text(payload, "createdBy");
        Instant updatedAt = UploadPayload.instant(payload, "updatedAt");
        String updatedBy = UploadPayload.text(payload, "updatedBy");
        int version = UploadPayload.intField(payload, "version");
        List<Object[]> movements = movementRows(id, UploadPayload.array(payload, "movements"));

        jdbc.update(SESSION_SQL, id, registerId, userId, status, timestamp(openedAt), openingAmount,
                timestamp(closedAt), expectedTotal, countedTotal, difference, countDetail, notes,
                timestamp(createdAt), createdBy, timestamp(updatedAt), updatedBy, version);
        for (Object[] movement : movements) {
            jdbc.update(MOVEMENT_SQL, movement);
        }
        return UploadOutcome.accepted();
    }

    /** Reads every movement before anything is written. A movement of an unknown type is invalid. */
    private static List<Object[]> movementRows(long sessionId, JsonNode movements) {
        List<Object[]> rows = new ArrayList<>(movements.size());
        for (JsonNode movement : movements) {
            String type = UploadPayload.text(movement, "type");
            if (!DEPOSIT.equals(type) && !WITHDRAWAL.equals(type)) {
                throw new InvalidPayloadException("type");
            }
            rows.add(new Object[] {
                UploadPayload.longField(movement, "id"),
                sessionId,
                type,
                UploadPayload.decimal(movement, "amount"),
                UploadPayload.text(movement, "reason"),
                timestamp(UploadPayload.instant(movement, "movedAt")),
                UploadPayload.longField(movement, "userId"),
                UploadPayload.optionalLong(movement, "authorizedBy"),
                timestamp(UploadPayload.instant(movement, "createdAt")),
                UploadPayload.text(movement, "createdBy"),
                timestamp(UploadPayload.instant(movement, "updatedAt")),
                UploadPayload.text(movement, "updatedBy"),
                UploadPayload.intField(movement, "version")
            });
        }
        return rows;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
