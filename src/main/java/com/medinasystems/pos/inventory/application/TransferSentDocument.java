package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.domain.InventoryTransfer;
import com.medinasystems.pos.inventory.domain.InventoryTransferLine;
import com.medinasystems.pos.inventory.domain.InventoryTransferSerial;

/**
 * A transfer as the origin store sends it (event INVENTORY_TRANSFER_SENT): the header, its lines and serial numbers, the
 * TRANSFER_OUT movements of the origin, the stock of the products afterwards, and the serial units that moved with their
 * links. Ids are the store's own. Field names are part of the protocol; the central node reads them in
 * {@link TransferSentUploadHandler}.
 */
public record TransferSentDocument(long id, String number, long originBranchId, long destinationBranchId, String notes,
                                   String sendIdempotencyKey, String sentAt, long sentBy, String by,
                                   List<LineDocument> lines, List<TransferSerialDocument> transferSerials,
                                   List<InventoryDocumentDocument.MovementDocument> movements,
                                   List<InventoryDocumentDocument.ItemDocument> items,
                                   List<InventoryDocumentDocument.SerialDocument> serials,
                                   List<InventoryDocumentDocument.MovementSerialDocument> movementSerials) {

    public static final String EVENT_TYPE = "INVENTORY_TRANSFER_SENT";

    /** One product of the transfer, with the quantity sent and the average cost of the origin. */
    public record LineDocument(long id, long productId, BigDecimal quantity, BigDecimal unitCost) {
    }

    /** One serial number of a line. {@code received} is false when sent. */
    public record TransferSerialDocument(long id, long lineId, String serialNumber, boolean received,
                                         String externalCode, Map<String, Object> attributes) {
    }

    static TransferSentDocument of(InventoryTransfer transfer, List<StockMovement> moved, List<InventoryItem> items,
                                   SerialTrail.Trail trail, String by) {
        return new TransferSentDocument(transfer.getId(), transfer.getNumber(), transfer.getOriginBranchId(),
                transfer.getDestinationBranchId(), transfer.getNotes(), transfer.getSendIdempotencyKey(),
                transfer.getSentAt().toString(), transfer.getSentBy(), by,
                lineDocuments(transfer.getLines()), transferSerials(transfer.getLines()),
                moved.stream().map(InventoryDocumentDocument.MovementDocument::of).toList(),
                items.stream().map(InventoryDocumentDocument.ItemDocument::of).toList(),
                trail.serials().stream().map(InventoryDocumentDocument.SerialDocument::of).toList(),
                trail.links().stream().map(InventoryDocumentDocument.MovementSerialDocument::of).toList());
    }

    static List<LineDocument> lineDocuments(List<InventoryTransferLine> lines) {
        return lines.stream()
                .map(line -> new LineDocument(line.getId(), line.getProductId(), line.getQuantity(),
                        line.getUnitCost()))
                .toList();
    }

    static List<TransferSerialDocument> transferSerials(List<InventoryTransferLine> lines) {
        return lines.stream()
                .flatMap(line -> line.getSerials().stream().map(serial -> serialDocument(line, serial)))
                .toList();
    }

    private static TransferSerialDocument serialDocument(InventoryTransferLine line, InventoryTransferSerial serial) {
        return new TransferSerialDocument(serial.getId(), line.getId(), serial.getSerialNumber(), serial.isReceived(),
                serial.getExternalCode(), serial.getAttributes());
    }
}
