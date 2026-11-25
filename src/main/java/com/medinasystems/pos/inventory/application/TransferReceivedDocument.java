package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.util.List;

import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.domain.InventoryTransfer;

/**
 * The receipt of a transfer as the destination store uploads it (event INVENTORY_TRANSFER_RECEIVED): the transfer id and the
 * receipt fields, each line with the quantity received, each serial with its received flag, the TRANSFER_IN movements of the
 * destination, the stock afterwards and the serial units that moved with their links. The ids of the movements and the units
 * are the destination's own. Field names are part of the protocol; the central node reads them in
 * {@link TransferReceivedUploadHandler}.
 */
public record TransferReceivedDocument(long transferId, long destinationBranchId, String status, String receivedAt,
                                       long receivedBy, String receiveNotes, String receiveIdempotencyKey, String by,
                                       List<ReceivedLineDocument> lines,
                                       List<TransferSentDocument.TransferSerialDocument> transferSerials,
                                       List<InventoryDocumentDocument.MovementDocument> movements,
                                       List<InventoryDocumentDocument.ItemDocument> items,
                                       List<InventoryDocumentDocument.SerialDocument> serials,
                                       List<InventoryDocumentDocument.MovementSerialDocument> movementSerials) {

    public static final String EVENT_TYPE = "INVENTORY_TRANSFER_RECEIVED";

    /** One line of the transfer with the quantity the destination received. */
    public record ReceivedLineDocument(long id, BigDecimal receivedQuantity) {
    }

    static TransferReceivedDocument of(InventoryTransfer transfer, List<StockMovement> moved, List<InventoryItem> items,
                                       SerialTrail.Trail trail, String by) {
        List<ReceivedLineDocument> lines = transfer.getLines().stream()
                .map(line -> new ReceivedLineDocument(line.getId(), line.getReceivedQuantity()))
                .toList();
        return new TransferReceivedDocument(transfer.getId(), transfer.getDestinationBranchId(),
                transfer.getStatus().name(), transfer.getReceivedAt().toString(), transfer.getReceivedBy(),
                transfer.getReceiveNotes(), transfer.getReceiveIdempotencyKey(), by, lines,
                TransferSentDocument.transferSerials(transfer.getLines()),
                moved.stream().map(InventoryDocumentDocument.MovementDocument::of).toList(),
                items.stream().map(InventoryDocumentDocument.ItemDocument::of).toList(),
                trail.serials().stream().map(InventoryDocumentDocument.SerialDocument::of).toList(),
                trail.links().stream().map(InventoryDocumentDocument.MovementSerialDocument::of).toList());
    }
}
