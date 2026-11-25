package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.bool;
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
import com.medinasystems.pos.inventory.domain.TransferStatus;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: stores a transfer that the origin store sent (INVENTORY_TRANSFER_SENT). The transfer keeps the ids of the store;
 * its lines and serial numbers are inserted with them, the TRANSFER_OUT movements and the stock of the origin are written, and
 * the change log replicates the transfer to every store.
 *
 * <p>The checks are completeness, the origin branch, the id range of the store, the links between the rows and the duplicate
 * number and key. The central node does not revalidate the stock. A missing branch, user or product gets RETRY and nothing is
 * written.
 */
@Component
@Profile("central")
public class TransferSentUploadHandler implements UploadHandler {

    private static final String INSERT_TRANSFER = "INSERT INTO inventory_transfer (id, number, origin_branch_id,"
            + " destination_branch_id, status, notes, sent_at, sent_by, send_idempotency_key, created_at, created_by,"
            + " updated_at, updated_by, version) OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)";
    private static final String INSERT_LINE = "INSERT INTO inventory_transfer_line (id, transfer_id, product_id, quantity,"
            + " received_quantity, unit_cost, created_at, created_by, updated_at, updated_by, version)"
            + " OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, 0)";
    private static final String INSERT_SERIAL = "INSERT INTO inventory_transfer_line_serial (id, line_id, serial_number,"
            + " received, created_at, created_by, updated_at, updated_by, version)"
            + " OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

    private final JdbcTemplate jdbc;
    private final StoreIdRanges ranges;
    private final BranchDirectory branches;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryUploadWriter writer;
    private final Clock clock;

    public TransferSentUploadHandler(JdbcTemplate jdbc, StoreIdRanges ranges, BranchDirectory branches, UserDirectory users,
                                     SaleableProducts products, InventoryUploadWriter writer, Clock clock) {
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
        return TransferSentDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        Upload upload;
        try {
            upload = Upload.from(payload);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (storeBranchId == null || storeBranchId != upload.originBranchId()) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        if (!upload.wellFormed()) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (!inRange(storeNodeId, upload)) {
            return UploadOutcome.rejected("ID_OUT_OF_RANGE");
        }
        // The store sends the transfer again when it has not seen the answer: a transfer that is already here is accepted.
        if (writer.exists("SELECT COUNT(*) FROM inventory_transfer WHERE id = ?", upload.id())) {
            return UploadOutcome.accepted();
        }
        if (writer.exists("SELECT COUNT(*) FROM inventory_transfer WHERE number = ?", upload.number())) {
            return UploadOutcome.rejected("DUPLICATE_TRANSFER_NUMBER");
        }
        if (upload.sendKey() != null && writer.exists("SELECT COUNT(*) FROM inventory_transfer"
                + " WHERE send_idempotency_key = ?", upload.sendKey())) {
            return UploadOutcome.rejected("DUPLICATE_IDEMPOTENCY_KEY");
        }
        if (!branches.exists(upload.originBranchId()) || !branches.exists(upload.destinationBranchId())) {
            return UploadOutcome.retry("BRANCH_MISSING");
        }
        if (users.find(upload.sentBy()).isEmpty()) {
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

    /** Every id that the store created must lie in the range of the store. */
    private boolean inRange(long storeNodeId, Upload upload) {
        if (!ranges.contains(storeNodeId, upload.id())) {
            return false;
        }
        for (Line line : upload.lines()) {
            if (!ranges.contains(storeNodeId, line.id())) {
                return false;
            }
        }
        for (TransferSerial serial : upload.transferSerials()) {
            if (!ranges.contains(storeNodeId, serial.id())) {
                return false;
            }
        }
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

    private void write(Upload upload) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update(INSERT_TRANSFER, upload.id(), upload.number(), upload.originBranchId(), upload.destinationBranchId(),
                TransferStatus.IN_TRANSIT.name(), upload.notes(), Timestamp.from(upload.sentAt()), upload.sentBy(),
                upload.sendKey(), now, upload.by(), now, upload.by());
        for (Line line : upload.lines()) {
            jdbc.update(INSERT_LINE, line.id(), upload.id(), line.productId(), line.quantity(), line.unitCost(), now,
                    upload.by(), now, upload.by());
        }
        for (TransferSerial serial : upload.transferSerials()) {
            jdbc.update(INSERT_SERIAL, serial.id(), serial.lineId(), serial.serialNumber(), serial.received(), now,
                    upload.by(), now, upload.by());
        }
        writer.writeMovements(upload.originBranchId(), upload.movements(), upload.by(), upload.by(), now.toInstant());
        Map<Long, Long> serialIds = writer.writeSerials(upload.serials(), upload.by(), upload.by(), now.toInstant());
        writer.writeMovementSerials(upload.movementSerials(), serialIds, upload.by(), upload.by(), now.toInstant());
        writer.writeItems(upload.originBranchId(), upload.items(), upload.by(), upload.by(), now.toInstant());
    }

    /** Lines and serial numbers of a payload, shared with the receipt handler. */
    static List<TransferSerial> transferSerialsFrom(JsonNode payload) {
        List<TransferSerial> serials = new ArrayList<>();
        for (JsonNode node : array(payload, "transferSerials")) {
            serials.add(new TransferSerial(longField(node, "id"), longField(node, "lineId"),
                    text(node, "serialNumber"), bool(node, "received")));
        }
        return serials;
    }

    /** A line of the transfer as sent. */
    record Line(long id, long productId, BigDecimal quantity, BigDecimal unitCost) {
    }

    /** A serial number of a line, with its received flag as sent (false) or as received. */
    record TransferSerial(long id, long lineId, String serialNumber, boolean received) {
    }

    /** Every field of an INVENTORY_TRANSFER_SENT payload, read and checked before anything is written. */
    private record Upload(long id, String number, long originBranchId, long destinationBranchId, String notes,
                          String sendKey, Instant sentAt, long sentBy, String by, List<Line> lines,
                          List<TransferSerial> transferSerials, List<Movement> movements, List<Item> items,
                          List<Serial> serials, List<MovementSerial> movementSerials) {

        static Upload from(JsonNode payload) {
            List<Line> lines = new ArrayList<>();
            for (JsonNode node : array(payload, "lines")) {
                lines.add(new Line(longField(node, "id"), longField(node, "productId"), decimal(node, "quantity"),
                        decimal(node, "unitCost")));
            }
            List<Movement> movements = InventoryUploadWriter.movementsFrom(payload);
            return new Upload(longField(payload, "id"), text(payload, "number"), longField(payload, "originBranchId"),
                    longField(payload, "destinationBranchId"), optionalText(payload, "notes"),
                    optionalText(payload, "sendIdempotencyKey"), instant(payload, "sentAt"),
                    longField(payload, "sentBy"), text(payload, "by"), lines, transferSerialsFrom(payload), movements,
                    InventoryUploadWriter.itemsFrom(payload), InventoryUploadWriter.serialsFrom(payload),
                    InventoryUploadWriter.movementSerialsFrom(payload));
        }

        /**
         * The rules that the database also enforces, and the links: two branches apart, positive quantities, the movements
         * of this transfer, and every link naming a unit and a movement of the payload.
         */
        boolean wellFormed() {
            if (originBranchId == destinationBranchId || lines.isEmpty()) {
                return false;
            }
            Set<Long> lineIds = new HashSet<>();
            for (Line line : lines) {
                if (line.quantity().signum() <= 0 || line.unitCost().signum() < 0 || !lineIds.add(line.id())) {
                    return false;
                }
            }
            for (TransferSerial serial : transferSerials) {
                if (!lineIds.contains(serial.lineId())) {
                    return false;
                }
            }
            for (Movement movement : movements) {
                if (movement.type() != StockMovementType.TRANSFER_OUT
                        || movement.referenceType() != StockReferenceType.TRANSFER || movement.referenceId() != id) {
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

        /** Every product the transfer touches: its lines, its movements and its stock rows. */
        List<Long> productIds() {
            List<Long> ids = new ArrayList<>();
            lines.forEach(line -> ids.add(line.productId()));
            movements.forEach(movement -> ids.add(movement.productId()));
            items.forEach(item -> ids.add(item.productId()));
            serials.forEach(unit -> ids.add(unit.productId()));
            return ids.stream().distinct().toList();
        }
    }
}
