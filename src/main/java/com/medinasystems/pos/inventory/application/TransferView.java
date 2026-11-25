package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medinasystems.pos.inventory.domain.InventoryTransfer;
import com.medinasystems.pos.inventory.domain.InventoryTransferLine;
import com.medinasystems.pos.inventory.domain.InventoryTransferSerial;
import com.medinasystems.pos.inventory.domain.TransferStatus;

/** A transfer with its lines and serial numbers, as the detail screen shows it. */
public record TransferView(long id, String number, long originBranchId, String originBranchName,
                           long destinationBranchId, String destinationBranchName, TransferStatus status, String notes,
                           Instant sentAt, long sentBy, String sentByName, Instant receivedAt, Long receivedBy,
                           String receivedByName, String receiveNotes, List<LineView> lines) {

    /** One product of the transfer: the quantities, the cost at which it left and its serial numbers. */
    public record LineView(long id, long productId, String productSku, String productName, BigDecimal quantity,
                           BigDecimal receivedQuantity, List<String> serialNumbers, List<String> receivedSerialNumbers,
                           BigDecimal unitCost) {
    }

    static TransferView of(InventoryTransfer transfer, InventoryNames names) {
        List<LineView> lines = transfer.getLines().stream().map(line -> lineView(line, names)).toList();
        return new TransferView(transfer.getId(), transfer.getNumber(), transfer.getOriginBranchId(),
                names.branchName(transfer.getOriginBranchId()), transfer.getDestinationBranchId(),
                names.branchName(transfer.getDestinationBranchId()), transfer.getStatus(), transfer.getNotes(),
                transfer.getSentAt(), transfer.getSentBy(), names.userName(transfer.getSentBy()),
                transfer.getReceivedAt(), transfer.getReceivedBy(),
                transfer.getReceivedBy() == null ? null : names.userName(transfer.getReceivedBy()),
                transfer.getReceiveNotes(), lines);
    }

    private static LineView lineView(InventoryTransferLine line, InventoryNames names) {
        List<String> serials = line.getSerials().stream().map(InventoryTransferSerial::getSerialNumber).toList();
        List<String> received = line.getSerials().stream().filter(InventoryTransferSerial::isReceived)
                .map(InventoryTransferSerial::getSerialNumber).toList();
        return new LineView(line.getId(), line.getProductId(), names.productSku(line.getProductId()),
                names.productName(line.getProductId()), line.getQuantity(), line.getReceivedQuantity(), serials,
                received, line.getUnitCost());
    }
}
