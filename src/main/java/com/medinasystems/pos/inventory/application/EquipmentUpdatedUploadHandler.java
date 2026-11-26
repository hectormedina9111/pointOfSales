package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalText;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: applies the edit of a unit's external code and characteristics made in a store. The unit is found by product
 * and serial number; the store must own the branch where the unit is. An older edit than the stored one is accepted without
 * change. A code that another unit already has is rejected.
 */
@Component
@Profile("central")
public class EquipmentUpdatedUploadHandler implements UploadHandler {

    private final JdbcTemplate jdbc;
    private final StoreIdRanges ranges;

    public EquipmentUpdatedUploadHandler(JdbcTemplate jdbc, StoreIdRanges ranges) {
        this.jdbc = jdbc;
        this.ranges = ranges;
    }

    @Override
    public String eventType() {
        return EquipmentUpdatedDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        Update update;
        try {
            update = Update.from(payload);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (storeBranchId == null || storeBranchId != update.branchId()) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        if (!ranges.contains(storeNodeId, update.id())) {
            return UploadOutcome.rejected("ID_OUT_OF_RANGE");
        }
        List<Map<String, Object>> stored = jdbc.queryForList("SELECT id, branch_id, updated_at FROM product_serial"
                + " WHERE product_id = ? AND serial_number = ?", update.productId(), update.serialNumber());
        if (stored.isEmpty()) {
            return UploadOutcome.retry("EQUIPMENT_MISSING");
        }
        long storedId = ((Number) stored.get(0).get("id")).longValue();
        if (((Number) stored.get(0).get("branch_id")).longValue() != update.branchId()) {
            return UploadOutcome.rejected("EQUIPMENT_NOT_IN_BRANCH");
        }
        Instant storedAt = ((Timestamp) stored.get(0).get("updated_at")).toInstant();
        if (!update.updatedAt().isAfter(storedAt)) {
            return UploadOutcome.accepted();
        }
        if (update.externalCode() != null && isUsedByOther(update.externalCode(), storedId)) {
            return UploadOutcome.rejected("DUPLICATE_EQUIPMENT_EXTERNAL_CODE");
        }
        jdbc.update("UPDATE product_serial SET external_code = ?, attributes = ?::jsonb, updated_at = ?,"
                        + " updated_by = ?, version = version + 1 WHERE id = ?",
                update.externalCode(), update.attributesJson(), Timestamp.from(update.updatedAt()), update.updatedBy(),
                storedId);
        return UploadOutcome.accepted();
    }

    private boolean isUsedByOther(String externalCode, long unitId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM product_serial WHERE external_code = ? AND id <> ?",
                Long.class, externalCode, unitId);
        return count != null && count > 0;
    }

    /** Every field of an EQUIPMENT_UPDATED payload, read and checked before anything is written. */
    private record Update(long id, long productId, String serialNumber, long branchId, String externalCode,
                          String attributesJson, Instant updatedAt, String updatedBy) {

        static Update from(JsonNode payload) {
            JsonNode attributes = payload.get("attributes");
            return new Update(longField(payload, "id"), longField(payload, "productId"),
                    text(payload, "serialNumber"), longField(payload, "branchId"), optionalText(payload, "externalCode"),
                    attributes == null || attributes.isNull() ? "{}" : attributes.toString(),
                    instant(payload, "updatedAt"), text(payload, "updatedBy"));
        }
    }
}
