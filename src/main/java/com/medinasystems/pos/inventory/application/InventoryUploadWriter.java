package com.medinasystems.pos.inventory.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.decimal;
import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalLong;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
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

    private final JdbcTemplate jdbc;

    InventoryUploadWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    /** The stock row of a product after the movements, with the copies of the product it carries. */
    record Item(long productId, BigDecimal quantity, BigDecimal averageCost, String sku, String name,
                Long categoryId, BigDecimal minStock, Instant lastMovementAt) {
    }
}
