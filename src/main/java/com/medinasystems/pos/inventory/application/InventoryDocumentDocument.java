package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.domain.InventoryDocument;
import com.medinasystems.pos.inventory.domain.InventoryDocumentLine;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.domain.InventoryMovementSerial;
import com.medinasystems.pos.inventory.domain.ProductSerial;

/**
 * An inventory document as a store uploads it (event INVENTORY_DOCUMENT): the header, its lines, its movements, the resulting
 * stock of each product it changed, and the serial units that its movements moved with the links between them. Ids are the
 * store's own. The central node also keeps the audit columns of the header; the rows below it get the time they were
 * received. Field names are part of the protocol; the central node reads them in {@link InventoryDocumentUploadHandler}.
 * Money and quantities are JSON numbers, instants ISO-8601 text.
 */
public record InventoryDocumentDocument(long id, String number, String type, long branchId, String reason, String notes,
                                        String idempotencyKey, String postedAt, long userId, String createdAt,
                                        String createdBy, String updatedAt, String updatedBy, Integer version,
                                        List<LineDocument> lines, List<MovementDocument> movements,
                                        List<ItemDocument> items, List<SerialDocument> serials,
                                        List<MovementSerialDocument> movementSerials) {

    public static final String EVENT_TYPE = "INVENTORY_DOCUMENT";

    public static InventoryDocumentDocument of(InventoryDocument document, List<StockMovement> movements,
                                               List<InventoryItem> items, SerialTrail.Trail trail) {
        return new InventoryDocumentDocument(document.getId(), document.getNumber(), document.getType().name(),
                document.getBranchId(), document.getReason(), document.getNotes(), document.getIdempotencyKey(),
                text(document.getPostedAt()), document.getUserId(), text(document.getCreatedAt()),
                document.getCreatedBy(), text(document.getUpdatedAt()), document.getUpdatedBy(),
                document.getVersion(),
                document.getLines().stream().map(LineDocument::of).toList(),
                movements.stream().map(MovementDocument::of).toList(),
                items.stream().map(ItemDocument::of).toList(),
                trail.serials().stream().map(SerialDocument::of).toList(),
                trail.links().stream().map(MovementSerialDocument::of).toList());
    }

    /** One line of the document. {@code countedQuantity} is set only on adjustments. */
    public record LineDocument(long id, long productId, BigDecimal quantity, BigDecimal countedQuantity,
                               BigDecimal previousQuantity, BigDecimal unitCost) {

        static LineDocument of(InventoryDocumentLine line) {
            return new LineDocument(line.getId(), line.getProductId(), line.getQuantity(), line.getCountedQuantity(),
                    line.getPreviousQuantity(), line.getUnitCost());
        }
    }

    /** One movement of the document, with the balance and the average cost after it. */
    public record MovementDocument(long id, long productId, String type, BigDecimal quantity,
                                   BigDecimal resultingBalance, BigDecimal unitCost, BigDecimal resultingAverageCost,
                                   String referenceType, long referenceId, String movedAt, long userId) {

        static MovementDocument of(StockMovement movement) {
            return new MovementDocument(movement.id(), movement.productId(), movement.type().name(),
                    movement.quantity(), movement.resultingBalance(), movement.unitCost(),
                    movement.resultingAverageCost(), movement.referenceType().name(), movement.referenceId(),
                    text(movement.movedAt()), movement.userId());
        }
    }

    /** The stock of a product in the branch after the document, with the copies of the product it carries. */
    public record ItemDocument(long productId, BigDecimal quantity, BigDecimal averageCost, String productSku,
                               String productName, Long categoryId, BigDecimal minStock, String lastMovementAt) {

        static ItemDocument of(InventoryItem item) {
            return new ItemDocument(item.getProductId(), item.getQuantity(), item.getAverageCost(),
                    item.getProductSku(), item.getProductName(), item.getCategoryId(), item.getMinStock(),
                    text(item.getLastMovementAt()));
        }
    }

    /**
     * One serial unit that a movement moved, with its state after them. The id is the store's: the central node keeps one row
     * per product and serial number, and maps the id to it (see {@link InventoryUploadWriter}).
     */
    public record SerialDocument(long id, long productId, String serialNumber, long branchId, String status,
                                 String receivedAt, String soldAt, Long lastSaleId, Long entryMovementId,
                                 String updatedAt, String updatedBy, Long categoryId, String externalCode,
                                 Map<String, Object> attributes) {

        static SerialDocument of(ProductSerial unit) {
            return new SerialDocument(unit.getId(), unit.getProductId(), unit.getSerialNumber(), unit.getBranchId(),
                    unit.getStatus().name(), text(unit.getReceivedAt()), text(unit.getSoldAt()), unit.getLastSaleId(),
                    unit.getEntryMovementId(), text(unit.getUpdatedAt()), unit.getUpdatedBy(), unit.getCategoryId(),
                    unit.getExternalCode(), unit.getAttributes());
        }
    }

    /** A link between a movement and a serial unit that it moved. */
    public record MovementSerialDocument(long id, long movementId, long serialId) {

        static MovementSerialDocument of(InventoryMovementSerial link) {
            return new MovementSerialDocument(link.getId(), link.getMovementId(), link.getSerialId());
        }
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
