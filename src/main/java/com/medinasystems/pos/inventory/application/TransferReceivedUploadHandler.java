package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.decimal;
import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalText;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Item;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Movement;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.MovementSerial;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Serial;
import com.medinasystems.pos.inventory.application.TransferSentUploadHandler.TransferSerial;
import com.medinasystems.pos.inventory.domain.TransferStatus;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: applies the receipt of a transfer that the destination store uploads (INVENTORY_TRANSFER_RECEIVED). The
 * central node updates the transfer, its lines and its serial numbers, writes the TRANSFER_IN movements of the destination and
 * the stock after them, and moves the received serial units to the destination. The serial units that did not arrive stay
 * IN_TRANSIT. The change log then replicates the final transfer to every store, the origin included.
 *
 * <p>The checks are completeness, the destination branch, the id range of the store, that the transfer is in transit or already
 * received with this same key, and that the lines belong to the transfer. The central node does not revalidate the receipt.
 */
@Component
@Profile("central")
public class TransferReceivedUploadHandler implements UploadHandler {

    private static final String UPDATE_TRANSFER = "UPDATE inventory_transfer SET status = ?, received_at = ?,"
            + " received_by = ?, receive_notes = ?, receive_idempotency_key = ?, updated_at = ?, updated_by = ?,"
            + " version = version + 1 WHERE id = ?";
    private static final String UPDATE_LINE = "UPDATE inventory_transfer_line SET received_quantity = ?, updated_at = ?,"
            + " updated_by = ?, version = version + 1 WHERE id = ?";
    private static final String UPDATE_SERIAL = "UPDATE inventory_transfer_line_serial SET received = ?, updated_at = ?,"
            + " updated_by = ?, version = version + 1 WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final StoreIdRanges ranges;
    private final BranchDirectory branches;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryUploadWriter writer;
    private final Clock clock;

    public TransferReceivedUploadHandler(JdbcTemplate jdbc, StoreIdRanges ranges, BranchDirectory branches,
                                         UserDirectory users, SaleableProducts products, InventoryUploadWriter writer,
                                         Clock clock) {
        this.jdbc = jdbc;
        this.ranges = ranges;
        this.branches = branches;
        this.users = users;
        this.products = products;
        this.writer = writer;
        this.clock = clock;
    }

    @Override
    public String eventType() {
        return TransferReceivedDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        Upload upload;
        try {
            upload = Upload.from(payload);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (storeBranchId == null || storeBranchId != upload.destinationBranchId()) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        if (!upload.wellFormed()) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (!inRange(storeNodeId, upload)) {
            return UploadOutcome.rejected("ID_OUT_OF_RANGE");
        }
        Map<String, Object> stored = storedTransfer(upload.transferId());
        if (stored == null) {
            // The transfer comes from the origin and may still be on its way to the central node.
            return UploadOutcome.retry("TRANSFER_MISSING");
        }
        if (((Number) stored.get("destination_branch_id")).longValue() != upload.destinationBranchId()) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        if (!TransferStatus.IN_TRANSIT.name().equals(stored.get("status"))) {
            // The store sends the receipt again when it has not seen the answer: the same receipt is accepted.
            return upload.receiveKey() != null && upload.receiveKey().equals(stored.get("receive_idempotency_key"))
                    ? UploadOutcome.accepted() : UploadOutcome.rejected("TRANSFER_NOT_IN_TRANSIT");
        }
        if (upload.receiveKey() != null && count("SELECT COUNT(*) FROM inventory_transfer"
                + " WHERE receive_idempotency_key = ? AND id <> ?", upload.receiveKey(), upload.transferId()) > 0) {
            return UploadOutcome.rejected("DUPLICATE_IDEMPOTENCY_KEY");
        }
        Map<Long, BigDecimal> sentQuantities = sentQuantities(upload.transferId());
        for (ReceivedLine line : upload.lines()) {
            BigDecimal sent = sentQuantities.get(line.id());
            if (sent == null) {
                return UploadOutcome.rejected("INVALID_PAYLOAD");
            }
            if (line.receivedQuantity().compareTo(sent) > 0) {
                return UploadOutcome.rejected("RECEIVED_EXCEEDS_SENT");
            }
        }
        Set<Long> serialIds = transferSerialIds(upload.transferId());
        for (TransferSerial serial : upload.transferSerials()) {
            if (!serialIds.contains(serial.id())) {
                return UploadOutcome.rejected("INVALID_PAYLOAD");
            }
        }
        if (!branches.exists(upload.destinationBranchId())) {
            return UploadOutcome.retry("BRANCH_MISSING");
        }
        if (users.find(upload.receivedBy()).isEmpty()) {
            return UploadOutcome.retry("USER_MISSING");
        }
        for (Movement movement : upload.movements()) {
            if (users.find(movement.userId()).isEmpty()) {
                return UploadOutcome.retry("USER_MISSING");
            }
        }
        for (long productId : upload.productIds()) {
            if (products.find(productId).isEmpty()) {
                return UploadOutcome.retry("PRODUCT_MISSING");
            }
        }
        write(upload);
        return UploadOutcome.accepted();
    }

    /** The movements, the serial units and their links are ids of the destination store; the rest belongs to the transfer. */
    private boolean inRange(long storeNodeId, Upload upload) {
        for (Movement movement : upload.movements()) {
            if (!ranges.contains(storeNodeId, movement.id())) {
                return false;
            }
        }
        for (Serial unit : upload.serials()) {
            if (!ranges.contains(storeNodeId, unit.id())) {
                return false;
            }
        }
        for (MovementSerial link : upload.movementSerials()) {
            if (!ranges.contains(storeNodeId, link.id())) {
                return false;
            }
        }
        return true;
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbc.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }

    private Map<String, Object> storedTransfer(long transferId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status, destination_branch_id,"
                + " receive_idempotency_key FROM inventory_transfer WHERE id = ?", transferId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The quantity sent of each line of the transfer, by line id. */
    private Map<Long, BigDecimal> sentQuantities(long transferId) {
        Map<Long, BigDecimal> quantities = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT id, quantity FROM inventory_transfer_line"
                + " WHERE transfer_id = ?", transferId)) {
            quantities.put(((Number) row.get("id")).longValue(), (BigDecimal) row.get("quantity"));
        }
        return quantities;
    }

    private Set<Long> transferSerialIds(long transferId) {
        Set<Long> ids = new HashSet<>(jdbc.queryForList("SELECT s.id FROM inventory_transfer_line_serial s"
                + " JOIN inventory_transfer_line l ON l.id = s.line_id WHERE l.transfer_id = ?", Long.class, transferId));
        return ids;
    }

    private void write(Upload upload) {
        Timestamp now = Timestamp.from(clock.instant());
        Timestamp received = Timestamp.from(upload.receivedAt());
        jdbc.update(UPDATE_TRANSFER, upload.status().name(), received, upload.receivedBy(), upload.receiveNotes(),
                upload.receiveKey(), now, upload.by(), upload.transferId());
        for (ReceivedLine line : upload.lines()) {
            jdbc.update(UPDATE_LINE, line.receivedQuantity(), now, upload.by(), line.id());
        }
        for (TransferSerial serial : upload.transferSerials()) {
            jdbc.update(UPDATE_SERIAL, serial.received(), now, upload.by(), serial.id());
        }
        writer.writeMovements(upload.destinationBranchId(), upload.movements(), upload.by(), upload.by(), now.toInstant());
        Map<Long, Long> serialIds = writer.writeSerials(upload.serials(), upload.by(), upload.by(), now.toInstant());
        writer.writeMovementSerials(upload.movementSerials(), serialIds, upload.by(), upload.by(), now.toInstant());
        writer.writeItems(upload.destinationBranchId(), upload.items(), upload.by(), upload.by(), now.toInstant());
    }

    /** A line of the receipt: the id of the line and the quantity received. */
    record ReceivedLine(long id, BigDecimal receivedQuantity) {
    }

    /** Every field of an INVENTORY_TRANSFER_RECEIVED payload, read and checked before anything is written. */
    private record Upload(long transferId, long destinationBranchId, TransferStatus status, Instant receivedAt,
                          long receivedBy, String receiveNotes, String receiveKey, String by, List<ReceivedLine> lines,
                          List<TransferSerial> transferSerials, List<Movement> movements, List<Item> items,
                          List<Serial> serials, List<MovementSerial> movementSerials) {

        static Upload from(JsonNode payload) {
            List<ReceivedLine> lines = new ArrayList<>();
            for (JsonNode node : array(payload, "lines")) {
                lines.add(new ReceivedLine(longField(node, "id"), decimal(node, "receivedQuantity")));
            }
            return new Upload(longField(payload, "transferId"), longField(payload, "destinationBranchId"),
                    InventoryUploadWriter.enumField(payload, "status", TransferStatus.class),
                    instant(payload, "receivedAt"), longField(payload, "receivedBy"),
                    optionalText(payload, "receiveNotes"), optionalText(payload, "receiveIdempotencyKey"),
                    text(payload, "by"), lines, TransferSentUploadHandler.transferSerialsFrom(payload),
                    InventoryUploadWriter.movementsFrom(payload), InventoryUploadWriter.itemsFrom(payload),
                    InventoryUploadWriter.serialsFrom(payload), InventoryUploadWriter.movementSerialsFrom(payload));
        }

        /**
         * The receipt is a receipt: a final status, unique lines with quantities that are not negative, serial rows of those
         * lines, TRANSFER_IN movements of this transfer, and links that name a unit and a movement of the payload.
         */
        boolean wellFormed() {
            if (status == TransferStatus.IN_TRANSIT) {
                return false;
            }
            Set<Long> lineIds = new HashSet<>();
            for (ReceivedLine line : lines) {
                if (line.receivedQuantity().signum() < 0 || !lineIds.add(line.id())) {
                    return false;
                }
            }
            for (TransferSerial serial : transferSerials) {
                if (!lineIds.contains(serial.lineId())) {
                    return false;
                }
            }
            for (Movement movement : movements) {
                if (movement.type() != StockMovementType.TRANSFER_IN
                        || movement.referenceType() != StockReferenceType.TRANSFER || movement.referenceId() != transferId) {
                    return false;
                }
            }
            Set<Long> units = new HashSet<>();
            serials.forEach(unit -> units.add(unit.id()));
            Set<Long> moves = new HashSet<>();
            movements.forEach(movement -> moves.add(movement.id()));
            return movementSerials.stream().allMatch(link -> units.contains(link.serialId())
                    && moves.contains(link.movementId()));
        }

        /** Every product the receipt touches: its movements and its stock rows. */
        List<Long> productIds() {
            List<Long> ids = new ArrayList<>();
            movements.forEach(movement -> ids.add(movement.productId()));
            items.forEach(item -> ids.add(item.productId()));
            serials.forEach(unit -> ids.add(unit.productId()));
            return ids.stream().distinct().toList();
        }
    }
}
