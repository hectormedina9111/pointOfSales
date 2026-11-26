package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.decimal;
import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalInstant;
import static com.medinasystems.pos.sync.UploadPayload.optionalLong;
import static com.medinasystems.pos.sync.UploadPayload.optionalText;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.domain.SerialStatus;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * The stock rows that an upload carries: the movements and the stock of each product after them. Shared by the central
 * node's upload handlers, so that every upload writes its movements and stock rows the same way. A stock row is written
 * only when the uploaded movement is newer than the stored one, so a late upload never overwrites a newer stock.
 */
@Component
@Profile("central")
final class InventoryUploadWriter {

    static final String MOVEMENT_SQL = "INSERT INTO inventory_movement (id, branch_id, product_id, type, quantity,"
            + " resulting_balance, unit_cost, resulting_average_cost, reference_type, reference_id, moved_at, user_id,"
            + " created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    static final String ITEM_SQL = "INSERT INTO inventory_item (product_id, branch_id, quantity, average_cost,"
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

    /**
     * The equipment data of a unit are kept when the upload carries none: a unit that came back from a store that never
     * edited it (or that was created by a return, which has no characteristics) must not erase what the central knows.
     */
    static final String SERIAL_INSERT_SQL = "INSERT INTO product_serial (id, product_id, category_id, serial_number,"
            + " branch_id, status, received_at, sold_at, last_sale_id, entry_movement_id, synced_at, external_code,"
            + " attributes, created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, 0)";
    static final String SERIAL_UPDATE_SQL = "UPDATE product_serial SET branch_id = ?, status = ?, received_at = ?,"
            + " sold_at = ?, last_sale_id = ?, entry_movement_id = ?, synced_at = COALESCE(synced_at, ?),"
            + " category_id = COALESCE(?, category_id), external_code = COALESCE(?, external_code),"
            + " attributes = CASE WHEN ?::jsonb = '{}'::jsonb THEN attributes ELSE ?::jsonb END,"
            + " updated_at = ?, updated_by = ?, version = version + 1 WHERE id = ?";
    static final String SERIAL_LINK_SQL = "INSERT INTO inventory_movement_serial (id, movement_id, serial_id, created_at,"
            + " created_by, updated_at, updated_by, version) VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

    private final JdbcTemplate jdbc;

    InventoryUploadWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The external code of an uploaded unit that another unit of the central node already carries, or null when every code is
     * free. The unit itself, with the same product and serial number, may keep its code.
     */
    String conflictingExternalCode(List<Serial> units) {
        for (Serial unit : units) {
            if (unit.externalCode() == null) {
                continue;
            }
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM product_serial WHERE external_code = ?"
                    + " AND NOT (product_id = ? AND serial_number = ?)", Long.class, unit.externalCode(),
                    unit.productId(), unit.serialNumber());
            if (count != null && count > 0) {
                return unit.externalCode();
            }
        }
        return null;
    }

    /** True when a query that counts rows with one argument finds at least one. */
    boolean exists(String sql, Object argument) {
        Long count = jdbc.queryForObject(sql, Long.class, argument);
        return count != null && count > 0;
    }

    /** Inserts the movements of a branch. The caller has checked the ids, the references and the duplicates. */
    void writeMovements(long branchId, List<Movement> movements, String createdBy, String updatedBy, Instant receivedAt) {
        Timestamp received = Timestamp.from(receivedAt);
        for (Movement movement : movements) {
            jdbc.update(MOVEMENT_SQL, movement.id(), branchId, movement.productId(), movement.type().name(),
                    movement.quantity(), movement.resultingBalance(), movement.unitCost(),
                    movement.resultingAverageCost(), movement.referenceType().name(), movement.referenceId(),
                    Timestamp.from(movement.movedAt()), movement.userId(), received, createdBy, received, updatedBy, 0);
        }
    }

    /**
     * Stores the serial units of an upload. A unit is one row per product and serial number on the central node: when the
     * unit is already stored under another id (it came from another node), that row is kept and the uploaded id is mapped to
     * it. The stored state changes only when the uploaded state is newer. Returns the central id of each uploaded unit.
     */
    Map<Long, Long> writeSerials(List<Serial> units, String createdBy, String updatedBy, Instant receivedAt) {
        Timestamp received = Timestamp.from(receivedAt);
        Map<Long, Long> ids = new HashMap<>();
        for (Serial unit : units) {
            List<Map<String, Object>> stored = jdbc.queryForList(
                    "SELECT id, updated_at FROM product_serial WHERE product_id = ? AND serial_number = ?",
                    unit.productId(), unit.serialNumber());
            if (stored.isEmpty()) {
                jdbc.update(SERIAL_INSERT_SQL, unit.id(), unit.productId(), unit.categoryId(), unit.serialNumber(),
                        unit.branchId(), unit.status().name(), Timestamp.from(unit.receivedAt()),
                        timestamp(unit.soldAt()), unit.lastSaleId(), unit.entryMovementId(), received,
                        unit.externalCode(), unit.attributesJson(), received, createdBy,
                        Timestamp.from(unit.updatedAt()), updatedBy);
                ids.put(unit.id(), unit.id());
                continue;
            }
            long storedId = ((Number) stored.get(0).get("id")).longValue();
            ids.put(unit.id(), storedId);
            Instant storedAt = ((Timestamp) stored.get(0).get("updated_at")).toInstant();
            if (unit.updatedAt().isAfter(storedAt)) {
                jdbc.update(SERIAL_UPDATE_SQL, unit.branchId(), unit.status().name(), Timestamp.from(unit.receivedAt()),
                        timestamp(unit.soldAt()), unit.lastSaleId(), unit.entryMovementId(), received,
                        unit.categoryId(), unit.externalCode(), unit.attributesJson(), unit.attributesJson(),
                        Timestamp.from(unit.updatedAt()), updatedBy, storedId);
            }
        }
        return ids;
    }

    /**
     * Inserts the links between movements and serial units. A link names its unit by the uploaded id, which
     * {@link #writeSerials} mapped to the central id.
     */
    void writeMovementSerials(List<MovementSerial> links, Map<Long, Long> serialIds, String createdBy, String updatedBy,
                              Instant receivedAt) {
        Timestamp received = Timestamp.from(receivedAt);
        for (MovementSerial link : links) {
            jdbc.update(SERIAL_LINK_SQL, link.id(), link.movementId(), serialIds.get(link.serialId()), received,
                    createdBy, received, updatedBy);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    /** Upserts the stock rows of a branch, each one only when it is newer than the stored one. */
    void writeItems(long branchId, List<Item> items, String createdBy, String updatedBy, Instant receivedAt) {
        Timestamp received = Timestamp.from(receivedAt);
        for (Item item : items) {
            jdbc.update(ITEM_SQL, item.productId(), branchId, item.quantity(), item.averageCost(), item.sku(),
                    item.name(), item.categoryId(), item.minStock(), Timestamp.from(item.lastMovementAt()), received,
                    createdBy, received, updatedBy);
        }
    }

    /** The movements of a payload, under "movements". Every field is read and checked before anything is written. */
    static List<Movement> movementsFrom(JsonNode payload) {
        List<Movement> movements = new ArrayList<>();
        for (JsonNode node : array(payload, "movements")) {
            movements.add(new Movement(longField(node, "id"), longField(node, "productId"),
                    enumField(node, "type", StockMovementType.class), decimal(node, "quantity"),
                    decimal(node, "resultingBalance"), decimal(node, "unitCost"),
                    decimal(node, "resultingAverageCost"), enumField(node, "referenceType",
                    StockReferenceType.class), longField(node, "referenceId"), instant(node, "movedAt"),
                    longField(node, "userId")));
        }
        return movements;
    }

    /** The serial units of a payload, under "serials". Each one is checked before anything is written. */
    static List<Serial> serialsFrom(JsonNode payload) {
        List<Serial> serials = new ArrayList<>();
        for (JsonNode node : array(payload, "serials")) {
            JsonNode attributes = node.get("attributes");
            serials.add(new Serial(longField(node, "id"), longField(node, "productId"), text(node, "serialNumber"),
                    longField(node, "branchId"), enumField(node, "status", SerialStatus.class),
                    instant(node, "receivedAt"), optionalInstant(node, "soldAt"), optionalLong(node, "lastSaleId"),
                    optionalLong(node, "entryMovementId"), instant(node, "updatedAt"), text(node, "updatedBy"),
                    optionalLong(node, "categoryId"), optionalText(node, "externalCode"),
                    attributes == null || attributes.isNull() ? "{}" : attributes.toString()));
        }
        return serials;
    }

    /** The movement-serial links of a payload, under "movementSerials". */
    static List<MovementSerial> movementSerialsFrom(JsonNode payload) {
        List<MovementSerial> links = new ArrayList<>();
        for (JsonNode node : array(payload, "movementSerials")) {
            links.add(new MovementSerial(longField(node, "id"), longField(node, "movementId"),
                    longField(node, "serialId")));
        }
        return links;
    }

    /** The stock rows of a payload, under "items". */
    static List<Item> itemsFrom(JsonNode payload) {
        List<Item> items = new ArrayList<>();
        for (JsonNode node : array(payload, "items")) {
            items.add(new Item(longField(node, "productId"), decimal(node, "quantity"),
                    decimal(node, "averageCost"), text(node, "productSku"), text(node, "productName"),
                    optionalLong(node, "categoryId"), decimal(node, "minStock"), instant(node, "lastMovementAt")));
        }
        return items;
    }

    static <E extends Enum<E>> E enumField(JsonNode payload, String name, Class<E> type) {
        String value = text(payload, name);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidPayloadException(name);
        }
    }

    /** A movement of a payload. */
    record Movement(long id, long productId, StockMovementType type, BigDecimal quantity,
                    BigDecimal resultingBalance, BigDecimal unitCost, BigDecimal resultingAverageCost,
                    StockReferenceType referenceType, long referenceId, Instant movedAt, long userId) {
    }

    /** A serial unit of a payload, with its state after the movements it took part in. */
    record Serial(long id, long productId, String serialNumber, long branchId, SerialStatus status, Instant receivedAt,
                  Instant soldAt, Long lastSaleId, Long entryMovementId, Instant updatedAt, String updatedBy,
                  Long categoryId, String externalCode, String attributesJson) {
    }

    /** A link between a movement and the serial unit it moved. */
    record MovementSerial(long id, long movementId, long serialId) {
    }

    /** The stock row of a product after the movements, with the copies of the product it carries. */
    record Item(long productId, BigDecimal quantity, BigDecimal averageCost, String sku, String name,
                Long categoryId, BigDecimal minStock, Instant lastMovementAt) {
    }
}
