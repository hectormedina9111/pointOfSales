package com.medinasystems.pos.inventory.application;

import java.time.Instant;

import com.medinasystems.pos.inventory.domain.InventoryTransfer;
import com.medinasystems.pos.inventory.domain.TransferStatus;

/** A transfer in the list: its header, without the lines. */
public record TransferSummaryView(long id, String number, long originBranchId, String originBranchName,
                                  long destinationBranchId, String destinationBranchName, TransferStatus status,
                                  Instant sentAt, String sentByName, Instant receivedAt) {

    static TransferSummaryView of(InventoryTransfer transfer, InventoryNames names) {
        return new TransferSummaryView(transfer.getId(), transfer.getNumber(), transfer.getOriginBranchId(),
                names.branchName(transfer.getOriginBranchId()), transfer.getDestinationBranchId(),
                names.branchName(transfer.getDestinationBranchId()), transfer.getStatus(), transfer.getSentAt(),
                names.userName(transfer.getSentBy()), transfer.getReceivedAt());
    }
}
