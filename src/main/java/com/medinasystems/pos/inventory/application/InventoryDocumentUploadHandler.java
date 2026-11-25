package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.decimal;
import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.intField;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalDecimal;
import static com.medinasystems.pos.sync.UploadPayload.optionalLong;
import static com.medinasystems.pos.sync.UploadPayload.optionalText;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
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
 * <p>The stock row of a product in a branch is written only when the uploaded movement is newer than the stored one, so
 * an older document that arrives late does not overwrite a newer stock.
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
    private static final String MOVEMENT_SQL = "INSERT INTO inventory_movement (id, branch_id, product_id, type, quantity,"
            + " resulting_balance, unit_cost, resulting_average_cost, reference_type, reference_id, moved_at, user_id,"
            + " created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String ITEM_SQL = "INSERT INTO inventory_item (product_id, branch_id, quantity, average_cost,"
            + " product_sku, product_name, category_id, min_stock, last_movement_at, created_at, created_by, updated_at,"
            + " updated_by, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"
            + " ON CONFLICT (product_id, branch_id) DO UPDATE SET quantity = EXCLUDED.quantity,"
            + " average_cost = EXCLUDED.average_cost, product_sku = EXCLUDED.product_sku,"
            + " product_name = EXCLUDED.product_name, category_id = EXCLUDED.category_id,"
            + " min_stock = EXCLUDED.min_stock, last_movement_at = EXCLUDED.last_movement_at,"
            + " updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by,"
            + " version = inventory_item.version + 1"
            + " WHERE inventory_item.last_movement_at IS NULL"
            + " OR inventory_item.last_movement_at < EXCLUDED.last_movement_at";

    private final JdbcTemplate jdbc;
    private final StoreIdRanges ranges;
    private final BranchDirectory branches;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final Clock clock;

    public InventoryDocumentUploadHandler(JdbcTemplate jdbc, StoreIdRanges ranges, BranchDirectory branches,
                                          UserDirectory users, SaleableProducts products, Clock clock) {
        this.jdbc = jdbc;
        this.ranges = ranges;
        this.branches = branches;
        this.users = users;
        this.products = products;
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
        if (!referencesItsDocument(upload)) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        // The store sends an item again when it has not seen the answer: a document that is already here is applied.
        if (exists("SELECT COUNT(*) FROM inventory_document WHERE id = ?", upload.id())) {
            return UploadOutcome.accepted();
        }
        if (exists("SELECT COUNT(*) FROM inventory_document WHERE number = ?", upload.number())) {
            return UploadOutcome.rejected("DUPLICATE_DOCUMENT_NUMBER");
        }
        if (upload.idempotencyKey() != null
                && exists("SELECT COUNT(*) FROM inventory_document WHERE idempotency_key = ?", upload.idempotencyKey())) {
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
        for (Movement movement : upload.movements()) {
            jdbc.update(MOVEMENT_SQL, movement.id(), upload.branchId(), movement.productId(), movement.type().name(),
                    movement.quantity(), movement.resultingBalance(), movement.unitCost(),
                    movement.resultingAverageCost(), movement.referenceType().name(), movement.referenceId(),
                    Timestamp.from(movement.movedAt()), movement.userId(), received, upload.createdBy(), received,
                    upload.updatedBy(), 0);
        }
        for (Item item : upload.items()) {
            jdbc.update(ITEM_SQL, item.productId(), upload.branchId(), item.quantity(), item.averageCost(), item.sku(),
                    item.name(), item.categoryId(), item.minStock(), Timestamp.from(item.lastMovementAt()), received,
                    upload.createdBy(), received, upload.updatedBy());
        }
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
        return true;
    }

    /** Every movement belongs to this document, so its reference must point to it. */
    private static boolean referencesItsDocument(Upload upload) {
        return upload.movements().stream().allMatch(movement ->
                movement.referenceType() == StockReferenceType.INVENTORY_DOCUMENT
                        && movement.referenceId() == upload.id());
    }

    private boolean exists(String sql, Object argument) {
        Long count = jdbc.queryForObject(sql, Long.class, argument);
        return count != null && count > 0;
    }

    /** A line of the document, read from the payload. */
    private record Line(long id, long productId, BigDecimal quantity, BigDecimal countedQuantity,
                        BigDecimal previousQuantity, BigDecimal unitCost) {
    }

    /** A movement of the document, read from the payload. */
    private record Movement(long id, long productId, StockMovementType type, BigDecimal quantity,
                            BigDecimal resultingBalance, BigDecimal unitCost, BigDecimal resultingAverageCost,
                            StockReferenceType referenceType, long referenceId, Instant movedAt, long userId) {
    }

    /** The stock row of a product after the document, read from the payload. */
    private record Item(long productId, BigDecimal quantity, BigDecimal averageCost, String sku, String name,
                        Long categoryId, BigDecimal minStock, Instant lastMovementAt) {
    }

    /** Every field of an INVENTORY_DOCUMENT payload, read and checked before anything is written. */
    private record Upload(long id, String number, InventoryDocumentType type, long branchId, String reason,
                          String notes, String idempotencyKey, Instant postedAt, long userId, Instant createdAt,
                          String createdBy, Instant updatedAt, String updatedBy, int version, List<Line> lines,
                          List<Movement> movements, List<Item> items) {

        static Upload from(JsonNode payload) {
            List<Line> lines = new ArrayList<>();
            for (JsonNode node : array(payload, "lines")) {
                lines.add(new Line(longField(node, "id"), longField(node, "productId"),
                        decimal(node, "quantity"), optionalDecimal(node, "countedQuantity"),
                        decimal(node, "previousQuantity"), optionalDecimal(node, "unitCost")));
            }
            List<Movement> movements = new ArrayList<>();
            for (JsonNode node : array(payload, "movements")) {
                movements.add(new Movement(longField(node, "id"), longField(node, "productId"),
                        enumField(node, "type", StockMovementType.class), decimal(node, "quantity"),
                        decimal(node, "resultingBalance"), decimal(node, "unitCost"),
                        decimal(node, "resultingAverageCost"), enumField(node, "referenceType",
                        StockReferenceType.class), longField(node, "referenceId"), instant(node, "movedAt"),
                        longField(node, "userId")));
            }
            List<Item> items = new ArrayList<>();
            for (JsonNode node : array(payload, "items")) {
                items.add(new Item(longField(node, "productId"), decimal(node, "quantity"),
                        decimal(node, "averageCost"), text(node, "productSku"), text(node, "productName"),
                        optionalLong(node, "categoryId"), decimal(node, "minStock"), instant(node, "lastMovementAt")));
            }
            if (lines.isEmpty() || movements.isEmpty()) {
                throw new InvalidPayloadException("lines");
            }
            return new Upload(longField(payload, "id"), text(payload, "number"),
                    enumField(payload, "type", InventoryDocumentType.class), longField(payload, "branchId"),
                    optionalText(payload, "reason"), optionalText(payload, "notes"),
                    optionalText(payload, "idempotencyKey"), instant(payload, "postedAt"),
                    longField(payload, "userId"), instant(payload, "createdAt"), text(payload, "createdBy"),
                    instant(payload, "updatedAt"), text(payload, "updatedBy"), intField(payload, "version"),
                    lines, movements, items);
        }

        /** Every product the document touches: in its lines, its movements and its stock rows. */
        List<Long> productIds() {
            List<Long> ids = new ArrayList<>();
            lines.forEach(line -> ids.add(line.productId()));
            movements.forEach(movement -> ids.add(movement.productId()));
            items.forEach(item -> ids.add(item.productId()));
            return ids.stream().distinct().toList();
        }

        private static <E extends Enum<E>> E enumField(JsonNode payload, String name, Class<E> type) {
            String value = text(payload, name);
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ex) {
                throw new InvalidPayloadException(name);
            }
        }
    }
}
