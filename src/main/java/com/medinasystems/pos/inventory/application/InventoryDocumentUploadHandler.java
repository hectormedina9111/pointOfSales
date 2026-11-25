package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.decimal;
import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.intField;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalDecimal;
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
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Item;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Movement;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.MovementSerial;
import com.medinasystems.pos.inventory.application.InventoryUploadWriter.Serial;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: stores an inventory document uploaded by a store, with its lines, its movements and the resulting stock of
 * the products it changed. Ids are the store's own, so every id must lie in the range of that store. The central node
 * checks completeness, the branch and the duplicates; it does not revalidate the stock. A product, branch or user that has
 * not arrived yet gets RETRY, and nothing is written.
 *
 * <p>The movements and the stock rows are written by {@link InventoryUploadWriter}, shared with the sales movements.
 */
@Component
@Profile("central")
public class InventoryDocumentUploadHandler implements UploadHandler {

    private static final String DOCUMENT_SQL = "INSERT INTO inventory_document (id, branch_id, number, type, reason,"
            + " notes, idempotency_key, posted_at, user_id, synced_at, created_at, created_by, updated_at, updated_by,"
            + " version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String LINE_SQL = "INSERT INTO inventory_document_line (id, document_id, product_id, quantity,"
            + " counted_quantity, previous_quantity, unit_cost, created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbc;
    private final StoreIdRanges ranges;
    private final BranchDirectory branches;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryUploadWriter writer;
    private final Clock clock;

    public InventoryDocumentUploadHandler(JdbcTemplate jdbc, StoreIdRanges ranges, BranchDirectory branches,
                                          UserDirectory users, SaleableProducts products,
                                          InventoryUploadWriter writer, Clock clock) {
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
        return InventoryDocumentDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        Upload upload;
        try {
            upload = Upload.from(payload);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (storeBranchId == null || storeBranchId != upload.branchId()) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        if (!idsInRange(storeNodeId, upload)) {
            return UploadOutcome.rejected("ID_OUT_OF_RANGE");
        }
        if (!referencesItsDocument(upload) || !linksItsUnits(upload)) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        // The store sends an item again when it has not seen the answer: a document that is already here is applied.
        if (writer.exists("SELECT COUNT(*) FROM inventory_document WHERE id = ?", upload.id())) {
            return UploadOutcome.accepted();
        }
        if (writer.exists("SELECT COUNT(*) FROM inventory_document WHERE number = ?", upload.number())) {
            return UploadOutcome.rejected("DUPLICATE_DOCUMENT_NUMBER");
        }
        if (upload.idempotencyKey() != null && writer.exists(
                "SELECT COUNT(*) FROM inventory_document WHERE idempotency_key = ?", upload.idempotencyKey())) {
            return UploadOutcome.rejected("DUPLICATE_IDEMPOTENCY_KEY");
        }
        if (!branches.exists(upload.branchId())) {
            return UploadOutcome.retry("BRANCH_MISSING");
        }
        if (users.find(upload.userId()).isEmpty()) {
            return UploadOutcome.retry("USER_MISSING");
        }
        for (long productId : upload.productIds()) {
            if (products.find(productId).isEmpty()) {
                return UploadOutcome.retry("PRODUCT_MISSING");
            }
        }
        write(upload, clock.instant());
        return UploadOutcome.accepted();
    }

    private void write(Upload upload, Instant receivedAt) {
        Timestamp received = Timestamp.from(receivedAt);
        jdbc.update(DOCUMENT_SQL, upload.id(), upload.branchId(), upload.number(), upload.type().name(),
                upload.reason(), upload.notes(), upload.idempotencyKey(), Timestamp.from(upload.postedAt()),
                upload.userId(), received, Timestamp.from(upload.createdAt()), upload.createdBy(),
                Timestamp.from(upload.updatedAt()), upload.updatedBy(), upload.version());
        for (Line line : upload.lines()) {
            jdbc.update(LINE_SQL, line.id(), upload.id(), line.productId(), line.quantity(), line.countedQuantity(),
                    line.previousQuantity(), line.unitCost(), received, upload.createdBy(), received,
                    upload.updatedBy(), 0);
        }
        writer.writeMovements(upload.branchId(), upload.movements(), upload.createdBy(), upload.updatedBy(), receivedAt);
        Map<Long, Long> serialIds = writer.writeSerials(upload.serials(), upload.createdBy(), upload.updatedBy(), receivedAt);
        writer.writeMovementSerials(upload.movementSerials(), serialIds, upload.createdBy(), upload.updatedBy(), receivedAt);
        writer.writeItems(upload.branchId(), upload.items(), upload.createdBy(), upload.updatedBy(), receivedAt);
    }

    private boolean idsInRange(long storeNodeId, Upload upload) {
        if (!ranges.contains(storeNodeId, upload.id())) {
            return false;
        }
        for (Line line : upload.lines()) {
            if (!ranges.contains(storeNodeId, line.id())) {
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

    /**
     * Every link names a unit and a movement of this document, both in the payload. The store sends the units its movements
     * moved in the same payload, so the link always finds its unit.
     */
    private static boolean linksItsUnits(Upload upload) {
        Set<Long> units = new HashSet<>();
        upload.serials().forEach(unit -> units.add(unit.id()));
        Set<Long> movements = new HashSet<>();
        upload.movements().forEach(movement -> movements.add(movement.id()));
        return upload.movementSerials().stream().allMatch(link -> units.contains(link.serialId())
                && movements.contains(link.movementId()));
    }

    /** Every movement belongs to this document, so its reference must point to it. */
    private static boolean referencesItsDocument(Upload upload) {
        return upload.movements().stream().allMatch(movement ->
                movement.referenceType() == StockReferenceType.INVENTORY_DOCUMENT
                        && movement.referenceId() == upload.id());
    }

    /** A line of the document, read from the payload. */
    private record Line(long id, long productId, BigDecimal quantity, BigDecimal countedQuantity,
                        BigDecimal previousQuantity, BigDecimal unitCost) {
    }

    /** Every field of an INVENTORY_DOCUMENT payload, read and checked before anything is written. */
    private record Upload(long id, String number, InventoryDocumentType type, long branchId, String reason,
                          String notes, String idempotencyKey, Instant postedAt, long userId, Instant createdAt,
                          String createdBy, Instant updatedAt, String updatedBy, int version, List<Line> lines,
                          List<Movement> movements, List<Item> items, List<Serial> serials,
                          List<MovementSerial> movementSerials) {

        static Upload from(JsonNode payload) {
            List<Line> lines = new ArrayList<>();
            for (JsonNode node : array(payload, "lines")) {
                lines.add(new Line(longField(node, "id"), longField(node, "productId"),
                        decimal(node, "quantity"), optionalDecimal(node, "countedQuantity"),
                        decimal(node, "previousQuantity"), optionalDecimal(node, "unitCost")));
            }
            List<Movement> movements = InventoryUploadWriter.movementsFrom(payload);
            List<Item> items = InventoryUploadWriter.itemsFrom(payload);
            List<Serial> serials = InventoryUploadWriter.serialsFrom(payload);
            List<MovementSerial> links = InventoryUploadWriter.movementSerialsFrom(payload);
            if (lines.isEmpty() || movements.isEmpty()) {
                throw new InvalidPayloadException("lines");
            }
            return new Upload(longField(payload, "id"), text(payload, "number"),
                    InventoryUploadWriter.enumField(payload, "type", InventoryDocumentType.class),
                    longField(payload, "branchId"), optionalText(payload, "reason"), optionalText(payload, "notes"),
                    optionalText(payload, "idempotencyKey"), instant(payload, "postedAt"),
                    longField(payload, "userId"), instant(payload, "createdAt"), text(payload, "createdBy"),
                    instant(payload, "updatedAt"), text(payload, "updatedBy"), intField(payload, "version"),
                    lines, movements, items, serials, links);
        }

        /** Every product the document touches: in its lines, its movements and its stock rows. */
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
